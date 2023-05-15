#!/bin/sh
set -eu

MAIN_CLASS=${MAIN_CLASS:-$(cat /app/jib-main-class-file)}
CLASS_PATH=${CLASS_PATH:-@/app/jib-classpath-file}

# shellcheck disable=SC2086,SC2068
exec java \
      -Duser.timezone=UTC \
      -XX:OnOutOfMemoryError='kill -9 %p' \
      -Djava.net.preferIPv4Stack=true \
      ${JAVA_OPTS:-} \
      ${JAVA_OPTS_MEM:-} \
      ${JAVA_OPTS_GC:-} \
      ${JAVA_OPTS_OTHER:-} \
      -classpath ${CLASS_PATH} \
      ${MAIN_CLASS} \
      $@
