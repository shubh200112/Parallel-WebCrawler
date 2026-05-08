package com.udacity.webcrawler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;

import javax.inject.Inject;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final Duration timeout;
  private final int popularWordCount;
  private final ForkJoinPool pool;
  private final PageParserFactory parserFactory;
private final int maxDepth;
private final List<Pattern> ignoredUrls;

@Inject
ParallelWebCrawler(
    Clock clock,
    PageParserFactory parserFactory,
    @Timeout Duration timeout,
    @PopularWordCount int popularWordCount,
    @MaxDepth int maxDepth,
    @IgnoredUrls List<Pattern> ignoredUrls,
    @TargetParallelism int threadCount) {

  this.clock = clock;
  this.parserFactory = parserFactory;
  this.timeout = timeout;
  this.popularWordCount = popularWordCount;
  this.maxDepth = maxDepth;
  this.ignoredUrls = ignoredUrls;

  this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
}

 @Override
public CrawlResult crawl(List<String> startingUrls) {

  Instant deadline = clock.instant().plus(timeout);

  ConcurrentMap<String, Integer> counts = new ConcurrentHashMap<>();

  Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

  List<CrawlTask> tasks = new ArrayList<>();

  for (String url : startingUrls) {
    tasks.add(
        new CrawlTask(
            url,
            deadline,
            maxDepth,
            counts,
            visitedUrls));
  }

  for (CrawlTask task : tasks) {
    pool.invoke(task);
  }

  Map<String, Integer> finalCounts;

  if (counts.isEmpty()) {
    finalCounts = counts;
  } else {
    finalCounts = WordCounts.sort(counts, popularWordCount);
  }

  return new CrawlResult.Builder()
      .setWordCounts(finalCounts)
      .setUrlsVisited(visitedUrls.size())
      .build();
}

private class CrawlTask extends RecursiveAction {

  private final String url;
  private final Instant deadline;
  private final int depth;
  private final ConcurrentMap<String, Integer> counts;
  private final Set<String> visitedUrls;

  CrawlTask(
      String url,
      Instant deadline,
      int depth,
      ConcurrentMap<String, Integer> counts,
      Set<String> visitedUrls) {

    this.url = url;
    this.deadline = deadline;
    this.depth = depth;
    this.counts = counts;
    this.visitedUrls = visitedUrls;
  }

  @Override
  protected void compute() {

    if (depth == 0 || clock.instant().isAfter(deadline)) {
      return;
    }

    for (Pattern pattern : ignoredUrls) {
      if (pattern.matcher(url).matches()) {
        return;
      }
    }

    if (!visitedUrls.add(url)) {
      return;
    }

    PageParser.Result result = parserFactory.get(url).parse();

    for (Map.Entry<String, Integer> e : result.getWordCounts().entrySet()) {

      counts.merge(e.getKey(), e.getValue(), Integer::sum);
    }

    List<CrawlTask> subtasks = new ArrayList<>();

    for (String link : result.getLinks()) {

      subtasks.add(
          new CrawlTask(
              link,
              deadline,
              depth - 1,
              counts,
              visitedUrls));
    }

    invokeAll(subtasks);
  }
}

  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }
}
