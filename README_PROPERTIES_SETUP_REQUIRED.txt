The following files need to be created in order for this project to build properly using the ANT build scripts:

build.properties
tomcat.properties
tomcattasks.properties

The contents should be as follows:

build.properties
#######################

FLEX_HOME=/path/to/local/flex/sdk
SDK_VERSION=sdk version number
FLEX_LIBS=${FLEX_HOME}/frameworks/libs
WEAVE_DOCROOT=/path/to/project
WEAVE_DESTROOT=/path/to/project/ROOT
ext.lib=/path/to/tomcat/lib

# These are the path to your local Tomcat installation
local_war=/path/to/local/tomcat
local_web=/path/to/local/tomcat/projectdir

###########################
tomcat.properties
###########################

test_path=WeaveServices

#Local server settings
devl.server=localhost
devl.manager.url=http\://${devl.server}\:8080/manager/text
devl.username=managerUserName
devl.password=password

# test server settings **old open.cridata.org VM MACHINE**
test.server=yourserver.org
test.manager.url=http\://${test.server}/manager/text
test.username=managerUserName
test.password=password

###########################
tomcattasks.properties (these are set for tomcat7)
###########################

# Tomcat Tasks Available to ANT
deploy=org.apache.catalina.ant.DeployTask
#install=org.apache.catalina.ant.InstallTask
list=org.apache.catalina.ant.ListTask
reload=org.apache.catalina.ant.ReloadTask
#remove=org.apache.catalina.ant.RemoveTask
resources=org.apache.catalina.ant.ResourcesTask
#roles=org.apache.catalina.ant.RolesTask
start=org.apache.catalina.ant.StartTask
stop=org.apache.catalina.ant.StopTask
undeploy=org.apache.catalina.ant.UndeployTask