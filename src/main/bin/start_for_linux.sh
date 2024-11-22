#!/usr/bin/bash

function parseInput() {
  cmd_line="$0"
  while [ $# -gt 0 ]; do
    case $1 in
      --java_home)
        java_home="$2"
        cmd_line=$cmd_line" --java_home "$java_home
        shift
        shift
        ;;
      *)
        other_args=${other_args}" "${1}
        cmd_line=$cmd_line" "$other_args
        shift
        ;;
    esac
  done
  echo $cmd_line
}

function startProcess() {
  SVC_CP=${WORK_DIR}/classes:${WORK_DIR}/lib/*
  echo "classpath is: $SVC_CP"

  if [[ ! -n ${java_home} ]]
  then
    echo "java_home HAS NOT BEEN SET. GOING TO USE JAVA AT JAVA_HOME: "
    echo $(java -version)
    nohup java -cp "${SVC_CP}" $JAVA_OPTS ${MAIN_CLASS} ${other_args}> /dev/null 2>&1 &
  else
    echo "java_home HAS BEEN SET TO: "$java_home
    nohup ${java_home}/bin/java -cp "${SVC_CP}" $JAVA_OPTS ${MAIN_CLASS} ${other_args} > /dev/null 2>&1 &
  fi

  if [ 0 -eq $? ]
  then
   echo $! | tee "${svc_pid}"
  fi
}

pushd . >/dev/null
cd `dirname $0`
cd ..
WORK_DIR=`pwd`
svc_pid=${WORK_DIR}/proc.pid
java_home=""
other_args=""
source ${WORK_DIR}/bin/variables.sh
pid=$(ps -aux | grep ${MAIN_CLASS} | grep -v grep | awk '{print $2}')

parseInput $@

JAVA_OPTS="-Duser.language=${USER_LANGUAGE} -Duser.country=${USER_COUNTRY}"
JAVA_OPTS="${JAVA_OPTS} -Xms256m -Xmx256m"
JAVA_OPTS="${JAVA_OPTS} -XX:MetaspaceSize=80m -XX:MaxMetaspaceSize=128m"
JAVA_OPTS="${JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=utf-8"

if [ -n "${pid}" ]
then
  echo process has exsited already.
else
  startProcess
fi
popd > /dev/null