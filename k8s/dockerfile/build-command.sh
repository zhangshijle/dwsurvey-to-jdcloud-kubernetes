#!/bin/bash

docker build -t myserver-cn-north-1.jcr.service.jdcloud.com/myserver/dwsurvey:v1 .


docker push myserver-cn-north-1.jcr.service.jdcloud.com/myserver/dwsurvey:v1
