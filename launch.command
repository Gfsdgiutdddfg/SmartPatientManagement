#!/bin/bash
cd "$(dirname "$0")"
if ! command -v java >/dev/null 2>&1; then echo "Java 17+ is required."; read -r -p "Press Enter to close"; exit 1; fi
if [ ! -f target/smart-patient-management.jar ]; then
  if ! command -v mvn >/dev/null 2>&1; then echo "Maven 3.9+ is required for the first build."; read -r -p "Press Enter to close"; exit 1; fi
  mvn clean package || { read -r -p "Build failed. Press Enter to close"; exit 1; }
fi
java -jar target/smart-patient-management.jar
