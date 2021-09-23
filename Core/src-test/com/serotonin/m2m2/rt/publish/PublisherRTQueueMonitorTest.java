/*
 * Copyright (C) 2021 RadixIot LLC. All rights reserved.
 */

package com.serotonin.m2m2.rt.publish;

import static com.serotonin.m2m2.rt.publish.PublishQueue.QUEUE_SIZE_MONITOR_ID;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.infiniteautomation.mango.spring.service.PublisherService;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.DataTypes;
import com.serotonin.m2m2.MangoTestBase;
import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.db.dao.DataSourceDao;
import com.serotonin.m2m2.db.dao.PublisherDao;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.rt.RuntimeManagerImpl;
import com.serotonin.m2m2.rt.dataImage.DataPointRT;
import com.serotonin.m2m2.rt.dataImage.PointValueTime;
import com.serotonin.m2m2.rt.publish.mock.MockPublisherRT;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.dataPoint.MockPointLocatorVO;
import com.serotonin.m2m2.vo.dataSource.mock.MockDataSourceVO;
import com.serotonin.m2m2.vo.publish.PublishedPointVO;
import com.serotonin.m2m2.vo.publish.PublisherVO;
import com.serotonin.m2m2.vo.publish.mock.MockPublishedPointVO;
import com.serotonin.m2m2.vo.publish.mock.MockPublisherDefinition;
import com.serotonin.m2m2.vo.publish.mock.MockPublisherVO;

public class PublisherRTQueueMonitorTest extends MangoTestBase {

    private MockDataSourceVO dataSource;
    private DataPointVO dataPoint;

    @BeforeClass
    public static void beforeTestClass() {
        //addModule("PublisherRTQueueMonitorTest", new MockPublisherDefinition());
        addModule("PublisherRTQueueMonitorTest", new TestPublisherDefinition());

    }

    @Before
    @Override
    public void before() {
        super.before();
        //Due to the test framework order of runtime manager injection had to add this
        ExecutorService executorService = Common.getBean(ExecutorService.class);
        DataSourceDao dataSourceDao = Common.getBean(DataSourceDao.class);
        PublisherDao publisherDao = Common.getBean(PublisherDao.class);
        DataPointDao dataPointDao = Common.getBean(DataPointDao.class);
        Common.runtimeManager = new RuntimeManagerImpl(executorService, dataSourceDao, publisherDao, dataPointDao);

        Common.runtimeManager.initialize(false);
        this.timer.setStartTime(System.currentTimeMillis());


    }

    @Test(timeout = 1 * 60 * 1000)
    public void testQueueMonitor() {
        //create DS and DP and enable them (need to be running)
        dataSource = this.createMockDataSource(true);
        dataPoint = this.createMockDataPoint(dataSource, new MockPointLocatorVO(DataTypes.NUMERIC, true), true);
        List<MockPublishedPointVO> points = new ArrayList<>(1);
        MockPublishedPointVO publishedPointVO = new MockPublishedPointVO();
        publishedPointVO.setDataPointId(dataPoint.getId());
        points.add(publishedPointVO);

        final String publisherVOXid = "TEST1";
        //create publisher with a published point (this is a listener) for the DP
        TestPublisherVO publisherVO = (TestPublisherVO) ModuleRegistry.getPublisherDefinition(MockPublisherDefinition.TYPE_NAME).baseCreatePublisherVO();
        publisherVO.setName("Name");
        publisherVO.setXid(publisherVOXid);
        publisherVO.setPoints(points);
        publisherVO.setEnabled(true);
        PublisherService publisherService = Common.getBean(PublisherService.class);
        publisherService.insert(publisherVO);

        //get the running data point RT from the Common.runtimeManager
        DataPointRT rt = Common.runtimeManager.getDataPoint(dataPoint.getId());

        //MockBackgroundProcessing
        //set PV(s) to the RT
        // Note: the publish point will be notified by DataPointEventNotifyWorkItem

        PointValueTime newValue = new PointValueTime(1.0, this.timer.currentTimeMillis());

        rt.setPointValue(newValue, null);


        try {
            /* Waiting for the PublishQueue.add to happen...
             * because I know that PublishQueue.lastSizeCheck is 0,
             * the fist time the PublishQueue.sizeCheck() will happen
             */
            publisherVO.getAddedFuture().get();

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

        //confirm publish queue is of certain size

        String monitor = QUEUE_SIZE_MONITOR_ID + publisherVOXid;
        Assert.assertNotNull(Common.MONITORED_VALUES.getMonitor(monitor).getName());
        Assert.assertEquals(1, Common.MONITORED_VALUES.getMonitor(monitor).getValue());
    }

    static class TestPublisherVO extends MockPublisherVO {
        private final CompletableFuture<Void> added = new CompletableFuture<>();

        @Override
        public PublisherRT<MockPublishedPointVO> createPublisherRT() {
            return new TestPublisherRT(this);
        }

        public CompletableFuture<Void> getAddedFuture() {
            return added;
        }


        private void writeObject(ObjectOutputStream out) throws IOException {

        }

        @SuppressWarnings("unchecked")
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {

        }
    }
    static class TestPublisherRT extends MockPublisherRT {

        public TestPublisherRT(PublisherVO<MockPublishedPointVO> vo) {
            super(vo);
        }

        @Override
        protected PublishQueue<MockPublishedPointVO, PointValueTime> createPublishQueue(PublisherVO<MockPublishedPointVO> vo) {

            TestPublisherVO tPvo = (TestPublisherVO)vo;
            return new TestPublishQueue(this, tPvo.getCacheWarningSize(), tPvo.getCacheDiscardSize(), tPvo.getAddedFuture());
        }
    }

    static class TestPublisherDefinition extends MockPublisherDefinition {
        @Override
        protected MockPublisherVO createPublisherVO() {
            MockPublisherVO pub = new TestPublisherVO();
            pub.setDefinition(this);
            return pub;
        }
    }

    static class TestPublishQueue extends PublishQueue {
        private final CompletableFuture<Void> added;

        public TestPublishQueue(PublisherRT owner, int warningSize, int discardSize, CompletableFuture<Void> added) {
            super(owner, warningSize, discardSize);
            this.added = added;
        }

        @Override
        public void add(PublishedPointVO vo, Object pvt) {
            super.add(vo, pvt);
            added.complete(null);
        }
    }
}