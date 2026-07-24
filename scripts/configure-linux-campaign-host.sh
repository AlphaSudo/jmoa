#!/usr/bin/env bash
set -euo pipefail

campaign_user="${1:-${SUDO_USER:-}}"
minimum_memory_bytes="${JMOA_MIN_TOTAL_MEMORY_BYTES:-8589934592}"
minimum_processors="${JMOA_MIN_LOGICAL_PROCESSORS:-4}"

if [[ "$(id -u)" -ne 0 ]]; then
  printf 'configure-linux-campaign-host: run with sudo/root\n' >&2
  exit 77
fi
if [[ -z "$campaign_user" ]] || ! id "$campaign_user" >/dev/null 2>&1; then
  printf 'configure-linux-campaign-host: valid campaign user is required\n' >&2
  exit 64
fi

mem_total_kb="$(awk '/^MemTotal:/ {print $2}' /proc/meminfo)"
mem_total_bytes="$((mem_total_kb * 1024))"
processor_count="$(getconf _NPROCESSORS_ONLN)"
if (( mem_total_bytes < minimum_memory_bytes )); then
  printf 'configure-linux-campaign-host: refusing setup; MemTotal=%s, required=%s\n' \
    "$mem_total_bytes" "$minimum_memory_bytes" >&2
  exit 78
fi
if (( processor_count < minimum_processors )); then
  printf 'configure-linux-campaign-host: refusing setup; processors=%s, required=%s\n' \
    "$processor_count" "$minimum_processors" >&2
  exit 78
fi

install -o root -g root -m 0755 /dev/stdin /usr/local/sbin/jmoa-drop-caches <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
sync
printf '3\n' > /proc/sys/vm/drop_caches
printf 'DROP_OK\n'
EOF

sudoers_path='/etc/sudoers.d/jmoa-campaign-drop-caches'
printf '%s ALL=(root) NOPASSWD: /usr/local/sbin/jmoa-drop-caches\n' "$campaign_user" > "$sudoers_path"
chmod 0440 "$sudoers_path"
visudo -cf "$sudoers_path"

swapoff -a
systemctl stop apt-daily.timer apt-daily-upgrade.timer apt-daily.service apt-daily-upgrade.service 2>/dev/null || true

printf 'campaign_user=%s\n' "$campaign_user"
printf 'mem_total_bytes=%s\n' "$mem_total_bytes"
printf 'logical_processors=%s\n' "$processor_count"
printf 'swap_total_bytes=%s\n' "$(awk '/^SwapTotal:/ {print $2 * 1024}' /proc/meminfo)"
printf 'drop_caches_helper_sha256=%s\n' "$(sha256sum /usr/local/sbin/jmoa-drop-caches | awk '{print $1}')"
printf 'sudoers_validation=PASSED\n'
