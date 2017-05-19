/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.module.definitions.event.detectors;

import com.serotonin.m2m2.vo.event.detector.AbstractEventDetectorVO;
import com.serotonin.m2m2.vo.event.detector.AnalogLowLimitDetectorVO;
import com.serotonin.m2m2.web.mvc.rest.v1.model.events.detectors.AbstractEventDetectorModel;
import com.serotonin.m2m2.web.mvc.rest.v1.model.events.detectors.AnalogLowLimitEventDetectorModel;

/**
 * @author Terry Packer
 *
 */
public class AnalogLowLimitEventDetectorDefinition extends PointEventDetectorDefinition<AnalogLowLimitDetectorVO>{

	public static final String TYPE_NAME = "LOW_LIMIT";
		
	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.EventDetectorDefinition#getEventDetectorSubTypeName()
	 */
	@Override
	public String getEventDetectorTypeName() {
		return TYPE_NAME;
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.EventDetectorDefinition#getDescriptionKey()
	 */
	@Override
	public String getDescriptionKey() {
		return "pointEdit.detectors.lowLimit";
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.EventDetectorDefinition#createEventDetectorVO()
	 */
	@Override
	protected AbstractEventDetectorVO<AnalogLowLimitDetectorVO> createEventDetectorVO() {
		return new AnalogLowLimitDetectorVO();
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.EventDetectorDefinition#createModel(com.serotonin.m2m2.vo.event.detector.AbstractEventDetectorVO)
	 */
	@Override
	public AbstractEventDetectorModel<AnalogLowLimitDetectorVO> createModel(
			AbstractEventDetectorVO<AnalogLowLimitDetectorVO> vo) {
		return new AnalogLowLimitEventDetectorModel((AnalogLowLimitDetectorVO)vo);
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.EventDetectorDefinition#getModelClass()
	 */
	@Override
	public Class<?> getModelClass() {
		return AnalogLowLimitEventDetectorModel.class;
	}

}
