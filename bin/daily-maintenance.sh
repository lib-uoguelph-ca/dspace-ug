#!/bin/bash
#############################################################################
#
#    Filename: daily-maintenance.sh
#      Author: Chris Charles
#
# ---------------------------------------------------------------------------
#
#    Perform daily DSpace maintenance tasks.
#
#    This script is designed to be run via cron. Normal behaviour should not
#    generate any output. Anything important (but normal) should be
#    logged. Exceptional behaviour should generate output to STDERR so it can
#    be emailed.
#
# ---------------------------------------------------------------------------
#
#    2011-01-11 Chris Charles <ccharles@uoguelph.ca>
#
#    * Added JAVA_OPTS environment variable to kill OutOfMemoryError
#
#    2011-01-06 Chris Charles <ccharles@uoguelph.ca>
#
#    * Initial version, based on separate non-logging crontab entries
#
#############################################################################

# Variables
DSPACE_HOME=/home/dspace
DSPACE_SRC=$DSPACE_HOME/dspace-ug
APPS=/apps
DSPACE=$APPS/dspace
DSPACE_CLI=$DSPACE/bin/dspace

LOG_DIR=$DSPACE_HOME/logs
LOG_FILE=$LOG_DIR/daily-maintenance.sh.log

# Ensure that the log directory exists
mkdir -p $LOG_DIR

# Set up the environment
source /etc/profile
export JAVA_OPTS=-Xmx1024m\ -Xms1024m


# Log the message with a timestamp.
#
# Example:
#
#    log_message "My message"
#
# appends something like
#
#    2011-01-04 14:19:12 My message
#
# to the log file
log_message() {
    echo `date "+%Y-%m-%d %H:%M:%S"` $@ >> $LOG_FILE
}

# Run the program with the given arguments and log the output from STDOUT. Do
# not include timestamps.
#
# Example:
#
#    log_run ls /tmp
#
# might append something like
#
#    directory1
#    file1
#    file2
#    symlink1
#
# to the log file
log_run() {
    $@ >> $LOG_FILE
}


# Housekeeping
log_message "=== Starting to run `basename $0`"

# Lift embargoes
log_message "--- Lifting embargoes"
log_run $DSPACE_CLI embargo-lifter

# Send out subscription emails
log_message "--- Send out subscription emails"
log_run $DSPACE_CLI sub-daily

# Generate plain text indices
log_message "--- Generating plain text indices"
log_run $DSPACE_CLI filter-media

# Check checksums and email the results
log_message "--- Verifying checksums"
log_run $DSPACE_CLI checker -lp
log_message "--- Emailing checksums"
log_run $DSPACE_CLI checker-emailer

# Generate sitemaps
log_message "--- Generating sitemaps"
log_run $DSPACE_CLI generate-sitemaps

# Update the database indices
log_message "--- Updating the database indices"
log_run $DSPACE_CLI index-update

