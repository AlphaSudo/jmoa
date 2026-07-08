# V2-J Public Second Runtime Target Selection

If Doctor remains blocked by the private runtime stack, the next portability
step should use a public service.

Recommended target:

```text
Spring PetClinic visits-service
```

Why:

```text
same public microservices repository as the confirmed customers-service work
different service surface from customers-service
Spring Boot deployment shape is familiar
public reproducibility is better than private Doctor runtime work
workload can be built from public endpoints
```

Alternative targets:

```text
Spring PetClinic vets-service
Spring PetClinic API gateway
another public Spring Boot service with simple Docker/Podman runtime
```

V2-K should not claim cross-service runtime generalization until the selected
service passes:

```text
semantic smoke
single runtime screen
V2-C confirmation if the screen passes
V2-D attribution
```
