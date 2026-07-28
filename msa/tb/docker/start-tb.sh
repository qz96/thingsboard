#!/bin/bash
#
# Copyright © 2016-2025 The Thingsboard Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

USE_BUILTIN_DB=false

# Check if using external database (skip built-in PostgreSQL)
if [ "${TB_USE_EXTERNAL_DB}" = "true" ]; then
    echo "TB_USE_EXTERNAL_DB=true, skipping built-in PostgreSQL."
else
    # Auto-detect: if datasource URL doesn't point to localhost, skip built-in DB
    ds_url="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/thingsboard}"
    if echo "$ds_url" | grep -qv "localhost\|127.0.0.1"; then
        echo "Datasource URL points to external host ($ds_url), skipping built-in PostgreSQL."
    else
        USE_BUILTIN_DB=true
        start-db.sh
    fi
fi

CONF_FOLDER="${pkg.installFolder}/conf"
jarfile=${pkg.installFolder}/bin/${pkg.name}.jar
configfile=${pkg.name}.conf
firstlaunch=${DATA_FOLDER}/.firstlaunch

source "${CONF_FOLDER}/${configfile}"

if [ ! -f ${firstlaunch} ]; then
    install-tb.sh --loadDemo && touch ${firstlaunch}
fi

if [ -f ${firstlaunch} ]; then
    echo "Starting ThingsBoard ..."

    java -cp ${jarfile} $JAVA_OPTS -Dloader.main=org.thingsboard.server.ThingsboardServerApplication \
                        -Dspring.jpa.hibernate.ddl-auto=none \
                        -Dlogging.config=${CONF_FOLDER}/logback.xml \
                        org.springframework.boot.loader.launch.PropertiesLauncher
else
    echo "ERROR: ThingsBoard is not installed"
fi

if [ "$USE_BUILTIN_DB" = "true" ]; then
    stop-db.sh
fi