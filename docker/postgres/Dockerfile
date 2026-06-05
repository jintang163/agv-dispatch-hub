FROM postgres:16-alpine

ENV TZ=Asia/Shanghai
ENV LANG=en_US.utf8

COPY ./init/ /docker-entrypoint-initdb.d/

RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

EXPOSE 5432

CMD ["postgres"]
