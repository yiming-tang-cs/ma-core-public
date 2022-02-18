/*
 * Copyright (C) 2022 Radix IoT LLC. All rights reserved.
 */

package com.serotonin.m2m2.db.dao.pointvalue;

import java.time.ZonedDateTime;
import java.time.temporal.TemporalAmount;
import java.util.stream.Stream;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.infiniteautomation.mango.db.iterators.StatisticsAggregator;
import com.infiniteautomation.mango.quantize.AnalogStatisticsQuantizer;
import com.infiniteautomation.mango.quantize.BucketCalculator;
import com.infiniteautomation.mango.quantize.TemporalAmountBucketCalculator;
import com.serotonin.m2m2.db.dao.PointValueDao;
import com.serotonin.m2m2.rt.dataImage.IdPointValueTime;
import com.serotonin.m2m2.rt.dataImage.PointValueTime;
import com.serotonin.m2m2.view.stats.DefaultSeriesValueTime;
import com.serotonin.m2m2.view.stats.IValueTime;
import com.serotonin.m2m2.view.stats.SeriesValueTime;
import com.serotonin.m2m2.vo.DataPointVO;

/**
 * @author Jared Wiltshire
 */
public interface AggregateDao {

    PointValueDao getPointValueDao();
    TemporalAmount getAggregationPeriod();

    default Stream<SeriesValueTime<AggregateValue>> query(DataPointVO point, ZonedDateTime from, ZonedDateTime to, @Nullable Integer limit) {
        var initialValue = Stream.generate(() -> getPointValueDao().initialValue(point, from.toInstant().toEpochMilli()))
                .limit(1);

        Stream<IdPointValueTime> stream = getPointValueDao().streamPointValues(point,
                from.toInstant().toEpochMilli(),
                to.toInstant().toEpochMilli(),
                limit, TimeOrder.ASCENDING);

        return aggregate(point, from, to, Stream.concat(initialValue, stream));
    }

    default Stream<SeriesValueTime<AggregateValue>> aggregate(DataPointVO point, ZonedDateTime from, ZonedDateTime to,
                                                              Stream<? extends PointValueTime> pointValues) {

        BucketCalculator bucketCalc = new TemporalAmountBucketCalculator(from, to, getAggregationPeriod());

        // TODO support non-analog statistic types

        return StatisticsAggregator.aggregate(pointValues, new AnalogStatisticsQuantizer(bucketCalc))
                .map(v -> new DefaultSeriesValueTime<>(point.getSeriesId(), v.getPeriodStartTime(), v));
    }

    default void save(DataPointVO point, Stream<? extends IValueTime<? extends AggregateValue>> aggregates, int chunkSize) {
        throw new UnsupportedOperationException();
    }

}
