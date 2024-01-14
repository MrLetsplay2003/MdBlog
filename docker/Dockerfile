FROM maven:latest as builder

COPY . /workspace

WORKDIR /workspace

RUN mvn clean package



FROM alpine:latest

ENV UID=1000
ENV GID=1000

RUN apk update && apk add openjdk17 shadow sudo

COPY --from=builder /workspace/target/MdBlog-*.jar /mdblog/MdBlog.jar
COPY --from=mrletsplay/docker_launcher /usr/local/bin/docker_launcher /usr/local/bin/docker_launcher
COPY ./docker/launcher_config.json /mdblog/launcher_config.json

RUN useradd mdblog

RUN mkdir /mdblog/data && chown -R mdblog /mdblog/data

VOLUME ["/mdblog/data"]

WORKDIR /mdblog/data

EXPOSE 3706

ENTRYPOINT [ "docker_launcher", "--config", "/mdblog/launcher_config.json", "sudo", "-u", "mdblog" ]
CMD [ "java", "-jar", "/mdblog/MdBlog.jar" ]
