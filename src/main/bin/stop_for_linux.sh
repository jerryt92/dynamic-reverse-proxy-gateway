#!/usr/bin/bash

function stopProcess() {
    kill ${pid}
    rm -f "${svc_pid}"
}

pushd . > /dev/null
cd `dirname $0`
cd ..
WORK_DIR=`pwd`
svc_pid=${WORK_DIR}/proc.pid
source ${WORK_DIR}/bin/variables.sh
pid=$(ps -aux | grep ${MAIN_CLASS} | grep -v grep | awk '{print $2}')

if [ -n "${pid}" ]
then
  stopProcess
else
  echo process is not exists.
fi
popd > /dev/null
