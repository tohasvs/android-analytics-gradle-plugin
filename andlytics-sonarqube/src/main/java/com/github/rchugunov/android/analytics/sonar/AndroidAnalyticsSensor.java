package com.github.rchugunov.android.analytics.sonar;

import com.github.rchugunov.android.analytics.sonar.model.GApiReviewsListResponse;
import com.github.rchugunov.android.analytics.sonar.model.Review;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.measures.Measure;
import org.sonar.api.resources.Project;

import java.io.File;
import java.io.FileReader;
import java.util.List;

public class AndroidAnalyticsSensor implements Sensor {

    /**
     * The file system object for the project being analysed.
     */
    private final FileSystem fileSystem;

    /**
     * The logger object for the sensor.
     */
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private Gson gson = new Gson();

    public AndroidAnalyticsSensor(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    /**
     * Analyses android.analytics/reviews.json
     * and extracts ratings From Google Play.
     *
     * @param module  the project being analysed
     * @param context the sensor context
     */
    @Override
    public void analyse(Project module, SensorContext context) {
        File rootDir = fileSystem.baseDir();

        File reviewsJsonFile = new File(rootDir, "build/android.analytics/reviews.json");

        if (!reviewsJsonFile.exists()) {
            return;
        }

        try {
            GApiReviewsListResponse response =
                    gson.fromJson(new FileReader(reviewsJsonFile), GApiReviewsListResponse.class);
            if (response != null) {
                processReviews(response.getReviews(), context);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processReviews(List<Review> reviews, SensorContext sensorContext) {
        if (reviews == null || reviews.size() == 0) {
            return;
        }
        double rating = 0;
        int actualReviewsCount = 0;
        for (Review review : reviews) {
            Review.Comment.UserComment comment = review.getComments().get(0).getUserComment();
            long ts = comment.getLastModified().getSeconds();
            if (ts < System.currentTimeMillis() / 1000 - 24 * 3600) {
                continue;
            }
            actualReviewsCount++;
            rating += comment.getStarRating();
        }

        if (rating > 0) {
            rating /= actualReviewsCount;
        }

        Measure measure = new Measure(AndroidAnalyticsMetrics.GOOGLE_PLAY_RATING, rating);
        sensorContext.saveMeasure(measure);
    }

    /**
     * Determines whether the sensor should run or not for the given project.
     *
     * @param project the project being analysed
     * @return always true
     */

    @Override
    public boolean shouldExecuteOnProject(Project project) {
        // this sensor is executed on any type of project
        return true;
    }
}
