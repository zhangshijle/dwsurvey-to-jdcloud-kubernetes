FROM openjdk:8u312-buster 

RUN mkdir /apps


ADD docker-entrypoint.sh /usr/local/bin/
RUN ln -s /usr/local/bin/docker-entrypoint.sh /entrypoint.sh
ADD dwsurvey-oss-vue-v.5.3.0-Beta.jar /apps/ 


EXPOSE 8080

ENTRYPOINT ["docker-entrypoint.sh"]
