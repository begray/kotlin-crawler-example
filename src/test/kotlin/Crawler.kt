package com.stdcall.example.crawler

import io.kotlintest.matchers.contain
import io.kotlintest.matchers.have
import io.kotlintest.specs.FlatSpec
import java.net.URL

class ExtractorTest : FlatSpec() {
    val extractor = Extractor()
    val baseUrl = URL("http://example.com")

    init {
        "Extractor.extract" should "extract single link" {
            val html = "<a href=\"http://example.com\" />"

            val links = extractor.extract(baseUrl, html)

            links should have size 1
            links should contain element URL("http://example.com")
        }

        "Extractor.extract" should "extract multiple links" {
            val html = "<a href=\"http://example.com/1\" />" +
                    "<a href=\"http://example.com/2\" />" +
                    "<a href=\"http://example.com/3\" />"

            val links = extractor.extract(baseUrl, html)

            links should have size 3
            links should contain element URL("http://example.com/1")
            links should contain element URL("http://example.com/2")
            links should contain element URL("http://example.com/3")
        }

        "Extractor.extract" should "skip non-base urls" {
            val html = "<a href=\"http://example.com/1\" />" +
                    "<a href=\"http://example1.com\" />" +
                    "<a href=\"//example1.com\" />" +
                    "<a href=\"http://example2.com\" />"

            val links = extractor.extract(baseUrl, html)

            links should have size 1
            links should contain element URL("http://example.com/1")
        }

        "Extractor.extract" should "handle single quotes" {
            val html = "<a href=\'http://example.com\' />"

            val links = extractor.extract(baseUrl, html)

            links should have size 1
            links should contain element URL("http://example.com")
        }

        "Extractor.extract" should "reconstruct protocol relative urls" {
            val html = "<a href=\'//example.com\' />"

            val links = extractor.extract(baseUrl, html)

            links should have size 1
            links should contain element URL("http://example.com")
        }

        "Extractor.extract" should "reconstruct relative urls" {
            val html = "<a href=\'/wiki\' />"

            val links = extractor.extract(baseUrl, html)

            links should have size 1
            links should contain element URL("http://example.com/wiki")
        }
    }

    // TODO add some integration tests for archiver and crawler
}
