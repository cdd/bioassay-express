#!/usr/bin/env bash

trap "eval cleanup" SIGINT

# last zip-file processed
lastzip=

usage() {
  echo "usage: $0 -t <tmpdir> -a <assays_dir> -g <greptext>"
  exit 1
}

cleanup() {
  if [ "${lastzip}" != "" ]; then
    rootname=`rootname ${lastzip}`    
    if [ -d ${TMPDIR}/${rootname} ]; then
      rm -rf ${TMPDIR}/${rootname} 
    fi
  fi
  exit 1
}

rootname() {
  local filename=$1
  local basename=`basename ${filename}`
  local rootname=${basename%.zip}
  echo ${rootname}
}

GREPTEXT=
TMPDIR=${HOME}/tmp
ASSAYS_DIR=/opt/bae/assays

while getopts ":t:g:a:" opt; do
  case $opt in
    t)
      # tmp directory where zip-files housing assays are unpacked
      TMPDIR=$OPTARG
      ;;
    g)
      # text to be grep'd in assay files
      GREPTEXT=$OPTARG
      ;;
    a)
      # directory where zip'd assays are housed
      ASSAYS_DIR=$OPTARG
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      exit 1
      ;;
    :)
      echo "Option -$OPTARG requires an argument." >&2
      exit 1
      ;;
  esac
done

if [ "${GREPTEXT}" == "" ]; then
  echo "Please specify text to be grep'd in assays."
  usage
fi

if [ ! -d "${TMPDIR}" ]; then
  echo "Please make sure the directory specified for temporary files exists."
  usage
fi

if [ ! -d "${ASSAYS_DIR}" ]; then
  echo "Please make sure the directory housing assays exists."
  usage
fi

for i in `ls ${ASSAYS_DIR}/*.zip`; do
  unzip -qq -d ${TMPDIR} ${i}
  if [ $? != 0 ]; then
    dir=`dirname ${i}`
    echo "error: make sure you have permissions to open files in the directory ${dir}"
    exit 1
  fi
  lastzip=${i}

  rootname=`rootname ${i}`
  (cd ${TMPDIR}/${rootname} ; for j in `ls *.gz`; do
    result=`gzcat ${j} | grep -i "${GREPTEXT}"`
    if [ "${result}" != "" ]; then
      echo -e "\n${i} : ${j}"
    else
      echo -n "."
    fi
  done)

  if [ -d ${TMPDIR}/${rootname} ]; then
    # clean up unpacked directory of assays
    rm -rf ${TMPDIR}/${rootname}
  fi
done

exit 0
