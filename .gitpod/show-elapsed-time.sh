#!/bin/bash

start_time=$(date +%s)

# Start the main command in background
$1 &

# Get the PID of the background main command
pid=$!

# Start the timer in background
{
    while true; do
        current_time=$(date +%s)
        elapsed_time=$((current_time-start_time))
        echo -en "\rElapsed time: $elapsed_time secs "
        sleep 1
    done
} &

# Get the PID of the timer
timer_pid=$!

# Catch kill event in order to dispatch it to forked processes
trap "kill $pid $timer_pid; exit" SIGTERM
trap "kill $pid $timer_pid; exit" SIGINT

# Wait for the main command to finish
wait $pid

# Kill the timer
kill $timer_pid
