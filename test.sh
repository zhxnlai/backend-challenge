#!/bin/bash

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

msr='./build/install/msr-shadow/bin/msr'

function fail {
  echo -e "${RED}$1${NC}"
  exit 1
}

function pass {
  echo -e "${GREEN}Yes!${NC}"
}

function test_output {
  inp=$(</dev/stdin)
  [[ "$inp" =~ $1 ]] || fail "$inp $2"
  pass
}

function test_exit {
  [[ $? -eq 0 ]] || fail "$1"
  pass
}

function test_panic {
  [[ $? -ne 0 ]] || fail "$1"
  pass
}

function blast {
  urls=$(cat blast.txt)
  count=$(echo $urls | wc -w)
  echo "Registering ${count} URLs"
  $msr register $urls
  echo "Racing..."
  $msr race
}

echo "Installing module"
./gradlew installShadowDist
test_exit "install failed"

echo "Testing subcommand version"
$msr version | test_output "[[:digit:]].[[:digit:]].[[:digit:]]" "is not a semver"

echo "Testing register: invalid URL"
$msr register "httpbad"
test_panic "msr register did not fail"

echo "Testing register: valid URL"
$msr register "http://google.com"
test_exit "msr register failed"

echo "Testing measure"
$msr measure | test_output "google" "is missing an entry"

echo "Running race"
$msr race

echo "Blasting..."
time blast
