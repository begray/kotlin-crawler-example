package com.stdcall.example.crawler

import com.github.kittinunf.fuel.Fuel
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import mu.KotlinLogging
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

class Args(parser: ArgParser) {
    val rootUrl by parser.positional(
            "URL",
            "root url to crawl"
    ) { URL(this) }
    val depth by parser.storing(
            "-d",
            "--depth",
            help = "depth of crawl"
    ) { toInt() }.default(1)
    val concurrency by parser.storing(
            "-c",
            "--concurrency",
            help = "number of concurrently executed requests"
    ) { toInt() }.default(2)
    val outputDirectory by parser.storing(
            "-o",
            "--output",
            help = "output directory"
    ).default("output")
}

object Constants {
    // TODO handling pages with enormous number of outgoing links
    // TODO and as a result generating a lot of tasks will require additional work
    val MaxNumberOfPendingTasks = 1000000
}

class Archiver(val rootDirectory: String) {

    // TODO should we clean up output directory?

    fun archive(url: URL, document: String) {
        val parts = url.path.split("/")
        val directoryPart = listOf(url.host) + parts.subList(0, parts.size - 1)
        // TODO file name should have query params in it
        val filePart = parts[parts.size - 1]

        val directory = Paths.get(rootDirectory, *directoryPart.toTypedArray())
        val file = directory.resolve(filePart)

        Files.createDirectories(directory)

        file.toFile().printWriter().use { out ->
            out.print(document)
        }

        log.debug { "archived $url (${document.length} bytes)" }
    }
}

class Extractor {
    val linkRegex = Regex("<a\\s*?href=(\"|')(.*?)(\"|')")

    fun extract(url: URL, text: String): ArrayList<URL> {
        val result = ArrayList<URL>()

        for (match in linkRegex.findAll(text)) {
            val link = match.groupValues[2]

            // TODO parse link and check only host part for wikipedia.org

            if (link.contains(url.host)) {
                // here we only follow links within same domain name
                // (we don't want to scan the whole Internet, do we?)
                // as a result we want get different translations for the page
                // because those are hosted on different subdomains (e.g. ru.wikipedia.org)

                // TODO follow links to subdomains

                if (link.startsWith("//")) {
                    // add protocol to protocol relative urls
                    result.add(URL(url.protocol + ":" + link))
                } else {
                    result.add(URL(link))
                }
            } else if (link.startsWith("/") && !link.startsWith("//")) {
                // add protocol and host to relative urls
                result.add(URL(url.protocol + "://" + url.host + link))
            } else {
                log.trace { "dropped external link: $link" }
            }
        }

        log.info { "$url: found ${result.size} links." }

        return result
    }
}

data class Task(val url: URL, val depth: Int)

class Crawler(
        val numWorkers: Int,
        val archiver: Archiver,
        val extractor: Extractor
) {
    val processedLinks = ConcurrentHashMap.newKeySet<URL>()
    val taskQueue = LinkedBlockingQueue<Task>(Constants.MaxNumberOfPendingTasks)
    val freeWorkers = Semaphore(numWorkers)

    fun run(rootUrl: URL, depth: Int) {
        taskQueue.add(Task(rootUrl, depth))

        log.info { "webcrawler started crawling of $rootUrl with $depth depth" }

        while (taskQueue.size > 0 || freeWorkers.availablePermits() < numWorkers) {
            // for simplicity here we are 'actively' waiting for new tasks in queue by waking up every second.
            // better approach would be to notify main dispatcher thread when worker completed
            // another piece of crawling
            val task = taskQueue.poll(1, TimeUnit.SECONDS) ?: continue

            // wait for another worker to be available
            // limiting number of concurrent requests
            freeWorkers.acquire()

            // schedule asynchronous GET request

            // TODO we might want to change default timeouts here
            // TODO here we expect each URL to return text string, this won't work for images
            Fuel.get(task.url.toString()).responseString {
                request, _, result ->

                val url = request.url

                try {
                    result.fold(
                            { text ->
                                log.info { "completed crawling of $url successfully" }

                                archiver.archive(url, text)

                                if (task.depth > 0) {
                                    for (link in extractor.extract(url, text)) {
                                        if (!processedLinks.contains(link)) {
                                            taskQueue.add(Task(link, task.depth - 1))
                                            processedLinks.add(link)
                                        }
                                    }
                                }
                            },
                            { error ->
                                log.error(error) { "crawling of $url failed: $error" }

                                // write error message to disk too
                                archiver.archive(url, error.localizedMessage)
                            }
                    )
                } finally {
                    freeWorkers.release()
                }
            }
        }

        log.info { "done." }
    }
}


fun main(args: Array<String>): Unit = mainBody("crawler") {
    Args(ArgParser(args)).run {
        Crawler(concurrency, Archiver(outputDirectory), Extractor()).run(rootUrl, depth)
    }
}