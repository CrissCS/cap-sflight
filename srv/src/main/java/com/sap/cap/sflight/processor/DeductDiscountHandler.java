package com.sap.cap.sflight.processor;

import cds.gen.travelservice.DeductDiscountContext;
import cds.gen.travelservice.Travel;
import cds.gen.travelservice.TravelService_;
import static cds.gen.travelservice.TravelService_.TRAVEL;
import cds.gen.travelservice.Travel_;
import com.sap.cds.ql.Update;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import static java.lang.Boolean.TRUE;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;

@Component
@ServiceName(TravelService_.CDS_NAME)
public class DeductDiscountHandler implements EventHandler {

	private final PersistenceService persistenceService;
	private final DraftService draftService;

	public DeductDiscountHandler(PersistenceService persistenceService, DraftService draftService) {
		this.persistenceService = persistenceService;
		this.draftService = draftService;
	}

	@On(entity = Travel_.CDS_NAME)
	public void deductDiscount(final DeductDiscountContext context) {

		var travel = draftService.run(context.getCqn()).single(Travel.class);

		BigDecimal discount = BigDecimal.valueOf(context.getPercent())
				.divide(BigDecimal.valueOf(100), new MathContext(3));

		BigDecimal deductedBookingFee = travel.getBookingFee().subtract(travel.getBookingFee().multiply(discount))
				.round(new MathContext(3));
		BigDecimal deductedTotalPrice = travel.getTotalPrice().subtract(deductedBookingFee);

		travel.setBookingFee(deductedBookingFee);
		travel.setTotalPrice(deductedTotalPrice);

		Travel update = Travel.create();
		update.setTotalPrice(deductedTotalPrice);
		update.setBookingFee(deductedBookingFee);


		if (TRUE.equals(travel.getIsActiveEntity())) {
			var updateCqn = Update.entity(TRAVEL)
					.where(t -> t.TravelUUID().eq(travel.getTravelUUID())).data(update);
			persistenceService.run(updateCqn);
		} else {
			var updateCqn = Update.entity(TRAVEL)
					.where(t -> t.TravelUUID().eq(travel.getTravelUUID()).and(t.IsActiveEntity().eq(travel.getIsActiveEntity()))).data(update);
			draftService.patchDraft(updateCqn);
		}

		context.setResult(travel);
		context.setCompleted();
	}
}
