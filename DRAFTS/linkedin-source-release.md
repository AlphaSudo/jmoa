# LinkedIn Source Release Draft

I have now prepared the source-code side of JMOA v0.1.

The portfolio case studies came first; this source repo contains the build-time
Maven plugin, runtime adapter library, Spring Boot deployment/materialization
docs, runtime-origin verification guidance, and a public Spring PetClinic
no-CDS reproduction scaffold.

The most important lesson from making this public: JVM memory optimization is
not only bytecode rewriting. The source repo includes the packaging and runtime
verification layer because the experiments proved that classpath
materialization and runtime origins decide whether an optimizer's output is
real.

Source repo: https://github.com/AlphaSudo/jmoa
Portfolio: https://github.com/AlphaSudo/jmoa-jvm-optimization-portfolio
