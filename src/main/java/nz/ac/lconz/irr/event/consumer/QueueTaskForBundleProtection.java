package nz.ac.lconz.irr.event.consumer;

import org.dspace.content.Bundle;
import org.dspace.core.Constants;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.event.Event;

import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: schweer
 * Date: 20/01/12
 * Time: 10:40 AM
 * To change this template use File | Settings | File Templates.
 */
public class QueueTaskForBundleProtection extends QueueTaskOnEvent {
	@Override
	Item findItem(Context ctx, Event event) throws SQLException {
		Item result = null;
		if (event.getSubjectType() == Constants.ITEM) {
			result = (Item) event.getSubject(ctx);
		} else if (event.getSubjectType() == Constants.BUNDLE) {
			Bundle bundle = (Bundle) event.getSubject(ctx);
			Item[] items = bundle.getItems();
			if (items != null && items.length > 0) {
				result = items[0];
			}
		}
		if (result != null && result.isArchived()) {
			return result;
		}
		return null;
	}

	@Override
	boolean isApplicableEvent(Context ctx, Event event) {
		if (event.getSubjectType() == Constants.ITEM || event.getSubjectType() == Constants.BUNDLE) {
			return event.getEventType() == Event.ADD;
		}
		return false;
	}

	@Override
	String getTasksProperty() {
		return "queue.bundleprotect.tasks";
	}

	@Override
	String getQueueProperty() {
		return "queue.bundleprotect.name";
	}
}
