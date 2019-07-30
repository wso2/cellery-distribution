--
-- Copyright 2018 WSO2 Inc. (http://wso2.org)
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- SP --
-- SET GLOBAL max_connections = 10000;

DROP DATABASE IF EXISTS SP_MGT_DB;
DROP DATABASE IF EXISTS SP_WORKER_STATE_DB;
DROP DATABASE IF EXISTS cepDB;
DROP DATABASE IF EXISTS spDB;
DROP DATABASE IF EXISTS HTTP_ANALYTICS_DB;
DROP DATABASE IF EXISTS MESSAGE_TRACING_DB;
DROP DATABASE IF EXISTS WSO2AM_MGW_ANALYTICS_DB;
DROP DATABASE IF EXISTS WSO2AM_STATS_DB;
DROP DATABASE IF EXISTS CELLERY_OBSERVABILITY_DB;

CREATE DATABASE SP_MGT_DB;
CREATE DATABASE SP_WORKER_STATE_DB;
CREATE DATABASE cepDB;
CREATE DATABASE spDB;

CREATE DATABASE HTTP_ANALYTICS_DB;
CREATE DATABASE MESSAGE_TRACING_DB;
CREATE DATABASE WSO2AM_MGW_ANALYTICS_DB;
CREATE DATABASE WSO2AM_STATS_DB;
CREATE DATABASE CELLERY_OBSERVABILITY_DB CHARACTER SET latin1;

CREATE USER IF NOT EXISTS '{{ .Values.global.celleryRuntime.db.carbon.username }}'@'%' IDENTIFIED BY '{{ .Values.global.celleryRuntime.db.carbon.password }}';
GRANT ALL ON SP_MGT_DB.* TO '{{ .Values.global.celleryRuntime.db.carbon.username }}'@'%' IDENTIFIED BY '{{ .Values.global.celleryRuntime.db.carbon.password }}';
GRANT ALL ON SP_WORKER_STATE_DB.* TO '{{ .Values.global.celleryRuntime.db.carbon.username }}'@'%' IDENTIFIED BY '{{ .Values.global.celleryRuntime.db.carbon.password }}';
GRANT ALL ON cepDB.* TO '{{ .Values.global.celleryRuntime.db.carbon.username }}'@'%' IDENTIFIED BY '{{ .Values.global.celleryRuntime.db.carbon.password }}';
GRANT ALL ON spDB.* TO '{{ .Values.global.celleryRuntime.db.carbon.username }}'@'%' IDENTIFIED BY '{{ .Values.global.celleryRuntime.db.carbon.password }}';

GRANT ALL ON HTTP_ANALYTICS_DB.* TO '{{ .Values.global.celleryRuntime.db.carbon.username }}'@'%' IDENTIFIED BY '{{ .Values.global.celleryRuntime.db.carbon.password }}';
GRANT ALL ON MESSAGE_TRACING_DB.* TO '{{ .Values.global.celleryRuntime.db.carbon.username }}'@'%' IDENTIFIED BY '{{ .Values.global.celleryRuntime.db.carbon.password }}';
GRANT ALL ON WSO2AM_MGW_ANALYTICS_DB.* TO '{{ .Values.global.celleryRuntime.db.carbon.username }}'@'%' IDENTIFIED BY '{{ .Values.global.celleryRuntime.db.carbon.password }}';
GRANT ALL ON WSO2AM_STATS_DB.* TO '{{ .Values.global.celleryRuntime.db.carbon.username }}'@'%' IDENTIFIED BY '{{ .Values.global.celleryRuntime.db.carbon.password }}';
GRANT ALL ON CELLERY_OBSERVABILITY_DB.* TO '{{ .Values.global.celleryRuntime.db.carbon.username }}'@'%' IDENTIFIED BY '{{ .Values.global.celleryRuntime.db.carbon.password }}';

USE WSO2AM_STATS_DB;

CREATE TABLE IF NOT EXISTS AM_USAGE_UPLOADED_FILES (
    FILE_NAME varchar(255) NOT NULL,
    FILE_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FILE_PROCESSED tinyint(1) DEFAULT 0,
    FILE_CONTENT MEDIUMBLOB DEFAULT NULL,
    PRIMARY KEY (FILE_NAME, FILE_TIMESTAMP)
) ENGINE=InnoDB;;

USE WSO2AM_MGW_ANALYTICS_DB;

CREATE TABLE IF NOT EXISTS AM_USAGE_UPLOADED_FILES (
    FILE_NAME varchar(255) NOT NULL,
    FILE_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FILE_PROCESSED tinyint(1) DEFAULT 0,
    FILE_CONTENT MEDIUMBLOB DEFAULT NULL,
    PRIMARY KEY (FILE_NAME, FILE_TIMESTAMP)
) ENGINE=InnoDB;;
-- SP --