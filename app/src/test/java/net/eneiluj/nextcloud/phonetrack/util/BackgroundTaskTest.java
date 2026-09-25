package net.eneiluj.nextcloud.phonetrack.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class BackgroundTaskTest {

    /** Records which thread each step ran on. */
    private static class Probe extends BackgroundTask<Integer, Integer> {
        final List<String> events;
        Thread preThread;
        Thread backgroundThread;
        Thread postThread;
        Integer result;

        Probe(List<String> events) {
            this.events = events;
        }

        @Override
        protected void onPreExecute() {
            preThread = Thread.currentThread();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            backgroundThread = Thread.currentThread();
            events.add("run " + params[0]);
            return params[0] * 10;
        }

        @Override
        protected void onPostExecute(Integer result) {
            postThread = Thread.currentThread();
            this.result = result;
        }
    }

    private static void drainMainLooper() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void runsTheStepsOnTheRightThreads() throws Exception {
        Probe probe = new Probe(Collections.synchronizedList(new ArrayList<>()));
        probe.execute(4);
        BackgroundTask.awaitSerialQueue();
        drainMainLooper();

        Thread main = Looper.getMainLooper().getThread();
        assertSame(main, probe.preThread);
        assertNotSame(main, probe.backgroundThread);
        assertSame(main, probe.postThread);
        assertEquals(Integer.valueOf(40), probe.result);
    }

    @Test
    public void serialTasksRunInSubmissionOrder() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        for (int i = 1; i <= 20; i++) {
            new Probe(events).execute(i);
        }
        BackgroundTask.awaitSerialQueue();

        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            expected.add("run " + i);
        }
        assertEquals(expected, events);
    }

    @Test
    public void parallelTasksRunConcurrently() throws Exception {
        // the first task waits for the second one: this would never finish if they were serial
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch bothDone = new CountDownLatch(2);
        new BackgroundTask<Void, Void>() {
            @Override
            protected Void doInBackground(Void... v) {
                try {
                    assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                bothDone.countDown();
                return null;
            }
        }.executeInParallel();
        new BackgroundTask<Void, Void>() {
            @Override
            protected Void doInBackground(Void... v) {
                secondStarted.countDown();
                bothDone.countDown();
                return null;
            }
        }.executeInParallel();

        assertTrue(bothDone.await(5, TimeUnit.SECONDS));
    }
}
