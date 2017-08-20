package com.stdcall.example.searcher

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import java.io.File

object Constants {
    val ANSI_RESET = "\u001B[0m"
    val ANSI_GREEN = "\u001B[32m"
}

class Args(parser: ArgParser) {
    val searchQuery by parser.positional(
            "QUERY"
    )
    val inputDirectory by parser.storing(
            "-i",
            "--input",
            help = "input directory"
    ).default("output")
}

class Searcher {
    fun run(rootDirectory: String, query: String) {
        File(rootDirectory).walkTopDown().forEach { f ->
            if (f.isFile) {

                // TODO properly handle different charsets

                f.readLines().forEachIndexed { idx, s ->

                    // TODO here we search in html, should we search only within content?

                    if (s.contains(query)) {

                        // TODO different types of formatting?
                        val colored = s.replace(query, Constants.ANSI_GREEN + query + Constants.ANSI_RESET)

                        println("$f:$idx\t$colored")
                    }
                }
            }
        }
    }
}

fun main(args: Array<String>): Unit = mainBody("searcher") {
    Args(ArgParser(args)).run {
        Searcher().run(inputDirectory, searchQuery)
    }
}
