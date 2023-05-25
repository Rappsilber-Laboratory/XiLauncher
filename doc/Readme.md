**xiLAUNCHER**

xiLauncher is intended to run xiSEARCH for searches defined in a database

**Download**

The latest binary can be downloaded from

https://github.com/Rappsilber-Laboratory/XiLauncher/releases

**START**

To start the launcher with a grafical interface, run:

java -jar XiLauncher.jar [path to config] [path to log-directory] --gui

* --gui is optional but allows to disable, enable and change individual queues in a graphical way.

Changes in the config file should be picked up during runtime but will not affect already running tasks.


**CONFIG**

The config file as several sections. The main ones are:

* *[JAR]* defines where to find the JAR files for the different xiSEARCH versions
* *[QUEUE]* defines a queue that can be used to run a search. Each enabled queue can run a search in parallel to all other queues. Main concern here is, that the sum of all memory used by all active queues should be smaller then the physically available memory in the server.
* *[DATABASE]* each database that should be can define tasks to be run (xi searches) need to be configured



Additionally there are some optional sections:

* *[INCLUDE]* defines a list of path to sub-configs to be parsed
* *[REPLACEMENTS]* can define a some variables that can be used in each [QUEUE] section
* *[PAUSE]* if present the launcher will not pick up any new search. Currently running tasks are not stopped
* *[STOP]* if present then no new searches are picked up and after the last currently running task is finished the launcher will exit.


**Section details**

*[JAR]*

Define the structure of the jar-files and where to find them.

Example:

	[JAR]
	base=xiSEARCH
	extension=jar
	path=/my_storage/software/xi/Versions/

path is optional and defaults to ".".
The path will be tested relative to the working directory of the launcher, relative to the current config and relative to the folder of the base config (only different if this section comes from an [INCLUDE])

the selected version will be searched as 

[path]/[base][version][extension]

or

[path]/[base]_[version][extension]

The path to the version is then made available as the variable %JAR%.


*[REPLACEMENT]*

The replacement section defines variables and basically helps to reuse text among different queues.

Example:
	[REPLACEMENTS]
	classpath=%JAR%:/data/rappstore/software/xi/lib7/*
	java=java

Arbitrary variable names can be assigned with any text. Also later variables can use earlier.

E.g. given

	a=I
	b=%a% am
	c=%b% here

%c% would be expanded to _"I am here"_

The variable %JAR% is a special case and is defined based on the [JAR] section and the selected version.


*[QUEUE]*

The [QUEUE] section defines (surprise) a queue and describes how tasks should be run.


Example:

	[QUEUE]
	QUEUE=small
	MAXFASTASIZE=20k
	ARGUMENTS=%java% -Xmx10G -Xms1G -DXI_DB_USER=%DBUSER% -DXI_DB_PASSWD=%DBPASS%  -DXI_SHOW_DEBUG=1 -DXI_EXTRA_CONFIG=UseCPUs:%LOW_CPU% -DXI_DB_CONNECTION=%DB%  -cp %classpath% -DXI_CSV_OUTPUT=/tmp/xi_csv_ouput_small.csv rappsilber.applications.XiDB
	enabled=true

_QUEUE_ defines the name of a queue (arbitrary text)

_MAXFASTASIZE_ defines that the Launcher should check the size of a fasta files selected for search and only use this queue for tasks that involve fasta-files smaller then this value

_ARGUMENTS_ defines what is actually started when a task is found

_enabled_ defines whether a queue will actually start anything.

There are some additionally settings that can be used:

_lowpriorityuser_ tasks from that user (as defined in the database) will have a lower priority

_priorityuser_ tasks from this user have a higher priority in this queue

_prioritisedpeaklistsize_ searches with peaklist smaller then the given size will be prioritised


Each queue runs a fair use policy. Basically that means while generally fist comes first served is used, when a user had two consecutive runs searched in the same queue another user will take the next spot (if another user has submitted a search).
This behaviour can be somewhat influenced by the _lowpriorityuser_, _priorityuser_ and _prioritisedpeaklistsize_ settings.

Several queues can be defined, each one with it's own section.


*[DATABASE]*

The [DATABASE] section defines what databse defines the tasks.

Example:

	CONNECTION=jdbc:postgresql://127.0.0.1:5432/my_xi_db
	USER=my_db_user
	PASSWORD=my_db_password


_CONNECTION_ the connection string to be used
_USER_ the databse user name to be used
_PASSWORD_ the database password to be used



**Service File**

To automatically start the launcher at boot you can create a service file as e.g. /etc/systemd/system/xiLauncher.service:


--------------------------------------------------
	[Unit]
	Description=xiLauncher
	StartLimitIntervalSec=300
	StartLimitBurst=10

	[Service]
	User=[user name] # optional
	ExecStart=/usr/bin/java -jar [path to XiLauncher.jar] [path to config] [path to log-directory]
	Restart=on-failure
	RestartSec=1s

	[Install]
	After=network-online.target # make sure we only start
	Wants=network-online.target # after network is up
	After=network-online.target # make sure we only start
	After=postgresql.service # if the database is running on the same server
	WantedBy=multi-user.target
--------------------------------------------------

Then you need to reload systemd:

	sudo systemctl daemon-reload

enable the new service:

	sudo systemctl enable xiLauncher

and start it:

	sudo systemctl start xiLauncher
	

Disadvantage of using it this way, is that no graphical interface can be shown. Meaning it is harder to track if something goes wrong or get stuck.

