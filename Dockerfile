FROM centos:7

RUN yum -y install java-11-openjdk

ENV TZ=Asia/Taipei
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /work
VOLUME /work/data

ARG JAR_FILE
ARG ACTIVE_PROFILE
ENV SPRING_PROFILES_ACTIVE=${ACTIVE_PROFILE}
ENV _JAVA_OPTIONS="-XX:+UnlockDiagnosticVMOptions -XX:+PrintFlagsFinal"
COPY ${JAR_FILE} /work/app.jar

#ENTRYPOINT ["java","-jar","-server","-d64","-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-XX:MaxRAMFraction=1", "app.jar"]
#ENTRYPOINT ["java","-jar", "-XX:MaxRAMPercentage=70", "-XX:MinRAMPercentage=20" "app.jar"]

ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,address=*:5005,server=y,suspend=n","-jar", "app.jar"]