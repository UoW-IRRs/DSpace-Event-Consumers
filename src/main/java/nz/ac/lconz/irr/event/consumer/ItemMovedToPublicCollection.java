package nz.ac.lconz.irr.event.consumer;

import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.Group;
import org.dspace.event.Event;

import java.sql.SQLException;

/**
 * Created with IntelliJ IDEA.
 * User: schweer
 * Date: 23/05/12
 * Time: 11:37 AM
 * To change this template use File | Settings | File Templates.
 */
public class ItemMovedToPublicCollection extends QueueTaskOnEvent {
	@Override
	Item findItem(Context ctx, Event event) throws SQLException {
		return (Item) event.getObject(ctx);
	}

	@Override
	boolean isApplicableEvent(Context ctx, Event event) throws SQLException {
		if (event.getSubjectType() != Constants.COLLECTION || event.getObjectType() != Constants.ITEM || event.getEventType() != Event.ADD) {
			return false;
		}
		Collection destinationCollection = (Collection) event.getSubject(ctx);
		return anonymousCanRead(ctx, destinationCollection);
	}

	private boolean anonymousCanRead(Context ctx, DSpaceObject dso) throws SQLException {
		Group[] readGroups = AuthorizeManager.getAuthorizedGroups(ctx, dso, Constants.READ);
		for (Group group : readGroups) {
			if (group.getID() == 0) {
				return true;
			}
		}
		return false;
	}

	@Override
	String getTasksProperty() {
		return "queue.item_moved_public_coll.tasks";
	}

	@Override
	String getQueueProperty() {
		return "queue.item_moved_public_coll.name";
	}
}
