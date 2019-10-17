FROM centos:7

RUN yum -y install java-1.8.0-openjdk

WORKDIR /work
VOLUME /work/data
VOLUME /work/assets

ARG JAR_FILE
ARG ACTIVE_PROFILE

ENV SPRING_PROFILES_ACTIVE=${ACTIVE_PROFILE}
COPY ${JAR_FILE} /work/app.jar

ENTRYPOINT ["java","-jar","app.jar"]