<#
.SYNOPSIS
    Deterministic JSON canonicalization for the PetClinic performance campaign (review Issue #7).

    The prior semantic comparison collapsed whitespace and hashed the result, which is not reliable JSON
    canonicalization (property ordering, volatile fields, and hypermedia links could cause false mismatch
    or false equivalence). This library instead:

      - parses the JSON;
      - recursively sorts object property names (ordinal);
      - normalizes explicitly declared volatile fields to a stable token;
      - PRESERVES array order (PetClinic collection order is contract-relevant);
      - serializes a canonical compact form;
      - hashes the canonical form.

    A normalization rule set is identified by a stable ruleId so the exact rule is recorded alongside each
    response. Actuator bodies are NOT compared by hash (they embed volatile disk/build/git metadata) - the
    caller compares those by status plus selected stable fields.

    Depends on runtime-automation-common.ps1 (Get-JmoaTextSha256).
#>
Set-StrictMode -Version Latest

# Default volatile field rule for the PetClinic customers-service JSON contract. Property names here are
# replaced with a stable token wherever they appear, so runtime-generated / temporal values never cause a
# false semantic mismatch. PetClinic owner/pet bodies are otherwise fully deterministic across identical
# fresh in-memory containers, so this list is intentionally conservative.
$script:JmoaCanonicalRules = @{
    'petclinic-owners-v1' = [ordered]@{
        ruleId         = 'petclinic-owners-v1'
        volatileFields = @('timestamp', 'lastModified', 'createdAt', 'updatedAt', '_links', 'href', 'self', 'traceId', 'spanId')
        description    = 'Sort keys ordinally; preserve array order; blank volatile temporal/hypermedia/trace fields.'
    }
    'identity-v1' = [ordered]@{
        ruleId         = 'identity-v1'
        volatileFields = @()
        description    = 'Sort keys ordinally; preserve array order; no volatile normalization.'
    }
}

function Get-JmoaCanonicalRule {
    param([string]$RuleId = 'petclinic-owners-v1')
    if ($script:JmoaCanonicalRules.Contains($RuleId)) { return $script:JmoaCanonicalRules[$RuleId] }
    return $script:JmoaCanonicalRules['identity-v1']
}

function ConvertTo-JmoaJsonStringLiteral {
    param([AllowEmptyString()][string]$Value)
    if ($null -eq $Value) { return 'null' }
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.Append('"')
    foreach ($ch in $Value.ToCharArray()) {
        switch ($ch) {
            '"'  { [void]$sb.Append('\"'); continue }
            '\'  { [void]$sb.Append('\\'); continue }
            "`b" { [void]$sb.Append('\b'); continue }
            "`f" { [void]$sb.Append('\f'); continue }
            "`n" { [void]$sb.Append('\n'); continue }
            "`r" { [void]$sb.Append('\r'); continue }
            "`t" { [void]$sb.Append('\t'); continue }
            default {
                if ([int][char]$ch -lt 32) { [void]$sb.Append(('\u{0:x4}' -f [int][char]$ch)) }
                else { [void]$sb.Append($ch) }
            }
        }
    }
    [void]$sb.Append('"')
    return $sb.ToString()
}

# Recursively renders a parsed JSON node into a canonical compact string.
function ConvertTo-JmoaCanonicalNode {
    param($Node, [string[]]$VolatileFields)
    if ($null -eq $Node) { return 'null' }
    if ($Node -is [bool]) { return $(if ($Node) { 'true' } else { 'false' }) }
    if ($Node -is [string]) { return (ConvertTo-JmoaJsonStringLiteral -Value $Node) }
    if ($Node -is [int] -or $Node -is [long] -or $Node -is [double] -or $Node -is [decimal] -or $Node -is [single]) {
        return ([string]([System.Convert]::ToString($Node, [System.Globalization.CultureInfo]::InvariantCulture)))
    }
    if ($Node -is [System.Collections.IDictionary]) {
        $keys = @($Node.Keys | Sort-Object { [string]$_ } -CaseSensitive)
        $parts = foreach ($k in $keys) {
            $keyName = [string]$k
            $valueText = if ($VolatileFields -contains $keyName) { '"<volatile>"' } else { ConvertTo-JmoaCanonicalNode -Node $Node[$k] -VolatileFields $VolatileFields }
            (ConvertTo-JmoaJsonStringLiteral -Value $keyName) + ':' + $valueText
        }
        return '{' + ($parts -join ',') + '}'
    }
    if ($Node -is [System.Management.Automation.PSCustomObject]) {
        $props = @($Node.PSObject.Properties | Sort-Object Name -CaseSensitive)
        $parts = foreach ($p in $props) {
            $valueText = if ($VolatileFields -contains $p.Name) { '"<volatile>"' } else { ConvertTo-JmoaCanonicalNode -Node $p.Value -VolatileFields $VolatileFields }
            (ConvertTo-JmoaJsonStringLiteral -Value $p.Name) + ':' + $valueText
        }
        return '{' + ($parts -join ',') + '}'
    }
    if ($Node -is [System.Collections.IEnumerable]) {
        $items = foreach ($item in $Node) { ConvertTo-JmoaCanonicalNode -Node $item -VolatileFields $VolatileFields }
        return '[' + (@($items) -join ',') + ']'
    }
    return (ConvertTo-JmoaJsonStringLiteral -Value ([string]$Node))
}

# Canonicalizes a JSON document string. Returns the canonical compact form, or throws if it is not JSON.
function ConvertTo-JmoaCanonicalJson {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Json, [string]$RuleId = 'petclinic-owners-v1')
    $rule = Get-JmoaCanonicalRule -RuleId $RuleId
    if ([string]::IsNullOrWhiteSpace($Json)) { return '' }
    $parsed = $Json | ConvertFrom-Json -Depth 64
    return ConvertTo-JmoaCanonicalNode -Node $parsed -VolatileFields @($rule.volatileFields)
}

# Convenience: canonicalize and hash. Returns a descriptor with canonical text + SHA-256 + ruleId, and a
# validJson flag so callers can fall back to a whitespace-normalized hash for non-JSON responses.
function Get-JmoaCanonicalJsonResult {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Body, [string]$RuleId = 'petclinic-owners-v1')
    if ([string]::IsNullOrWhiteSpace($Body)) {
        return [pscustomobject]@{ validJson = $false; ruleId = $RuleId; canonical = ''; sha256 = (Get-JmoaTextSha256 -Value ''); fallback = $true }
    }
    try {
        $canonical = ConvertTo-JmoaCanonicalJson -Json $Body -RuleId $RuleId
        return [pscustomobject]@{ validJson = $true; ruleId = $RuleId; canonical = $canonical; sha256 = (Get-JmoaTextSha256 -Value $canonical); fallback = $false }
    } catch {
        $normalized = ($Body -replace '\s+', ' ').Trim()
        return [pscustomobject]@{ validJson = $false; ruleId = $RuleId; canonical = $normalized; sha256 = (Get-JmoaTextSha256 -Value $normalized); fallback = $true }
    }
}
