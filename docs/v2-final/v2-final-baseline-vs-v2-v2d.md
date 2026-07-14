# Final B0 vs V2 V2-D Attribution

V2-D attributes the median win primarily to heap page-touch reduction:
heap PSS fell 6,584 KB while heap used changed by only 28 KB and histogram
bytes changed by only 29,184 bytes. Supporting changes include 1,612 KB lower
anonymous-RW PSS, about 3,128 KB lower metaspace committed, and fewer loaded
classes. The result is not a retained-object reduction claim and class count is
supporting evidence, not sole causality.
