package xln.common.dist

import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.CustomizableThreadFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

class SerialEventManager : Runnable{

    private val customThreadFactory = CustomizableThreadFactory("xln-serialEvent-")
    private val serializeExecutor = Executors.newFixedThreadPool(1, this.customThreadFactory)
    private val log = LoggerFactory.getLogger(this.javaClass);

    open class SerialEvent {

        private val latch = CountDownLatch(1);
        open fun run() {

        }
        fun postRun() {
            latch.countDown()
        }
        fun await() {
            latch.await()
        }
    }

    private class Poison : SerialEvent()
    private val queue = LinkedBlockingQueue<SerialEvent>()

    init {
        serializeExecutor.submit(this)
    }



    fun addEvent(event: SerialEvent) : SerialEvent {

        queue.add(event)
        return event;

    }

    fun clearAndWait() {
        queue.clear()
        open class WaitEvent : SerialEvent()
        val waitEvent = this.addEvent(WaitEvent())
        waitEvent.await()
    }

    //fun

    override fun run() {
        var quit = false;
        while(!quit) {
            val task = queue.take()

            try {
                task.run()
                task.postRun()
            }catch(ex : Exception) {
                log.error("", ex)
            }
            when(task) {
                is Poison -> quit = true
            }
        }

    }

    fun stop() {
        this.addEvent(Poison())
    }


}