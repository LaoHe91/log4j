/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.util;

import java.text.DecimalFormat;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Primarily used in unit tests, but can be used to track elapsed time for a request or portion of any other operation
 * so long as all the timer methods are called on the same thread in which it was started. Calling start on
 * multiple threads will cause the times to be aggregated.
 */
public class Timer implements StringBuilderFormattable {

    private final String name;        // The timer's name
    public enum Status {
        Started, Stopped, Paused
    }
    private Status status; // The timer's status
    private long elapsedTime;         // The elapsed time
    private final int iterations;
    private static final long NANO_PER_SECOND = 1000000000L;
    private static final long NANO_PER_MINUTE = NANO_PER_SECOND * 60;
    private static final long NANO_PER_HOUR = NANO_PER_MINUTE * 60;
    private final ThreadLocal<Long> startTime = ThreadLocal.withInitial(() -> 0L);
    private final Lock stateChangeLock = new ReentrantLock();


    /**
     * Constructor.
     * @param name the timer name.
     */
    public Timer(final String name)
    {
        this(name, 0);
    }

    /**
     * Constructor.
     *
     * @param name the timer name.
     * @param iterations the number of iterations that will take place.
     */
    public Timer(final String name, final int iterations)
    {
        this.name = name;
        status = Status.Stopped;
        this.iterations = (iterations > 0) ? iterations : 0;
    }

    /**
     * Start the timer.
     */
    public void start() {
        stateChangeLock.lock();
        try {
            startTime.set(System.nanoTime());
            elapsedTime = 0;
            status = Status.Started;
        } finally {
            stateChangeLock.unlock();
        }
    }

    public void startOrResume() {
        stateChangeLock.lock();
        try {
            if (status == Status.Stopped) {
                start();
            } else {
                resume();
            }
        } finally {
            stateChangeLock.unlock();
        }
    }

    /**
     * Stop the timer.
     * @return the String result of the timer completing.
     */
    public String stop() {
        stateChangeLock.lock();
        try {
            elapsedTime += System.nanoTime() - startTime.get();
            startTime.set(0L);
            status = Status.Stopped;
            return toString();
        } finally {
            stateChangeLock.unlock();
        }
    }

    /**
     * Pause the timer.
     */
    public void pause() {
        stateChangeLock.lock();
        try {
            elapsedTime += System.nanoTime() - startTime.get();
            startTime.set(0L);
            status = Status.Paused;
        } finally {
            stateChangeLock.unlock();
        }
    }

    /**
     * Resume the timer.
     */
    public void resume() {
        stateChangeLock.lock();
        try {
            startTime.set(System.nanoTime());
            status = Status.Started;
        } finally {
            stateChangeLock.unlock();
        }
    }

    /**
     * Accessor for the name.
     * @return the timer's name.
     */
    public String getName()
    {
        return name;
    }

    /**
     * Access the elapsed time.
     *
     * @return the elapsed time.
     */
    public long getElapsedTime()
    {
        return elapsedTime / 1000000;
    }

    /**
     * Access the elapsed time.
     *
     * @return the elapsed time.
     */
    public long getElapsedNanoTime()
    {
        return elapsedTime;
    }

    /**
     * Returns the name of the last operation performed on this timer (Start, Stop, Pause or
     * Resume).
     * @return the string representing the last operation performed.
     */
    public Status getStatus()
    {
        return status;
    }

    /**
     * Returns the String representation of the timer based upon its current state
     */
    @Override
    public String toString()
    {
        final StringBuilder result = new StringBuilder();
        formatTo(result);
        return result.toString();
    }

    @Override
    public void formatTo(final StringBuilder buffer) {
        buffer.append("Timer ").append(name);
        switch (status) {
            case Started:
                buffer.append(" started");
                break;
            case Paused:
                buffer.append(" paused");
                break;
            case Stopped:
                long nanoseconds = elapsedTime;
                // Get elapsed hours
                long hours = nanoseconds / NANO_PER_HOUR;
                // Get remaining nanoseconds
                nanoseconds = nanoseconds % NANO_PER_HOUR;
                // Get minutes
                long minutes = nanoseconds / NANO_PER_MINUTE;
                // Get remaining nanoseconds
                nanoseconds = nanoseconds % NANO_PER_MINUTE;
                // Get seconds
                long seconds = nanoseconds / NANO_PER_SECOND;
                // Get remaining nanoseconds
                nanoseconds = nanoseconds % NANO_PER_SECOND;

                String elapsed = Strings.EMPTY;

                if (hours > 0) {
                    elapsed += hours + " hours ";
                }
                if (minutes > 0 || hours > 0) {
                    elapsed += minutes + " minutes ";
                }

                DecimalFormat numFormat;
                numFormat = new DecimalFormat("#0");
                elapsed += numFormat.format(seconds) + '.';
                numFormat = new DecimalFormat("000000000");
                elapsed += numFormat.format(nanoseconds) + " seconds";
                buffer.append(" stopped. Elapsed time: ").append(elapsed);
                if (iterations > 0) {
                    nanoseconds = elapsedTime / iterations;
                    // Get elapsed hours
                    hours = nanoseconds / NANO_PER_HOUR;
                    // Get remaining nanoseconds
                    nanoseconds = nanoseconds % NANO_PER_HOUR;
                    // Get minutes
                    minutes = nanoseconds / NANO_PER_MINUTE;
                    // Get remaining nanoseconds
                    nanoseconds = nanoseconds % NANO_PER_MINUTE;
                    // Get seconds
                    seconds = nanoseconds / NANO_PER_SECOND;
                    // Get remaining nanoseconds
                    nanoseconds = nanoseconds % NANO_PER_SECOND;

                    elapsed = Strings.EMPTY;

                    if (hours > 0) {
                        elapsed += hours + " hours ";
                    }
                    if (minutes > 0 || hours > 0) {
                        elapsed += minutes + " minutes ";
                    }

                    numFormat = new DecimalFormat("#0");
                    elapsed += numFormat.format(seconds) + '.';
                    numFormat = new DecimalFormat("000000000");
                    elapsed += numFormat.format(nanoseconds) + " seconds";
                    buffer.append(" Average per iteration: ").append(elapsed);
                }
                break;
            default:
                buffer.append(' ').append(status);
                break;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Timer)) {
            return false;
        }

        final Timer timer = (Timer) o;

        if (elapsedTime != timer.elapsedTime) {
            return false;
        }
        if (startTime != timer.startTime) {
            return false;
        }
        if (name != null ? !name.equals(timer.name) : timer.name != null) {
            return false;
        }
        return status != null ? status.equals(timer.status) : timer.status == null;
    }

    @Override
    public int hashCode() {
        int result;
        result = (name != null ? name.hashCode() : 0);
        result = 29 * result + (status != null ? status.hashCode() : 0);
        final long time = startTime.get();
        result = 29 * result + (int) (time ^ (time >>> 32));
        result = 29 * result + (int) (elapsedTime ^ (elapsedTime >>> 32));
        return result;
    }

}
