package de.dlr.shepard.labJournal.services;

import de.dlr.shepard.labJournal.dao.LabJournalDAO;
import de.dlr.shepard.labJournal.entities.LabJournal;
import de.dlr.shepard.labJournal.io.LabJournalIO;
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.util.DateHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RequestScoped
public class LabJournalService {

  private LabJournalDAO labJournalDAO;

  private DataObjectDAO dataObjectDAO;

  private VersionDAO versionDAO;

  private CollectionDAO collectionDAO;

  private UserDAO userDAO;

  private DateHelper dateHelper;

  @Inject
  public LabJournalService(
    LabJournalDAO labJournalDAO,
    DataObjectDAO dataObjectDAO,
    VersionDAO versionDAO,
    CollectionDAO collectionDAO,
    UserDAO userDAO,
    DateHelper dateHelper
  ) {
    this.labJournalDAO = labJournalDAO;
    this.dataObjectDAO = dataObjectDAO;
    this.versionDAO = versionDAO;
    this.collectionDAO = collectionDAO;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
  }

  public LabJournal CreateLabJournal(LabJournalIO labJournalIO, String userName) {
    LabJournal labJournal = new LabJournal();
    User user = userDAO.find(userName);
    DataObject dataObject = dataObjectDAO.findByShepardId(labJournalIO.getDataObjectId());
    Collection collection = collectionDAO.findByShepardId(dataObject.getCollection().getId());
    labJournal.setDescription(labJournalIO.getJournalContent());
    labJournal.setCreatedBy(user);
    labJournal.setCreatedAt(dateHelper.getDate());
    labJournal.setDataObject(dataObject);
    labJournal = labJournalDAO.createOrUpdate(labJournal);
    labJournal.setShepardId(labJournal.getId());
    labJournal = labJournalDAO.createOrUpdate(labJournal);
    versionDAO.createLink(labJournal.getId(), collection.getVersion().getUid());
    return labJournal;
  }

  public List<LabJournal> getLabJournals(long objectId) {
    DataObject dataObject = dataObjectDAO.findByShepardId(objectId);
    if (null == dataObject) return new ArrayList<LabJournal>();
    return dataObject
      .getLabJournals()
      .stream()
      .sorted(Comparator.comparing(LabJournal::getCreatedAt))
      .collect(Collectors.toList());
  }

  public LabJournal getLabJournal(long labJournalId) {
    return labJournalDAO.findByShepardId(labJournalId);
  }

  public LabJournal updateLabJournal(long labJournalId, String labJournalContent, String userName) {
    LabJournal labJournal = labJournalDAO.findByShepardId(labJournalId);
    if (null == labJournal) return null;
    User user = userDAO.find(userName);
    labJournal.setDescription(labJournalContent);
    labJournal.setUpdatedAt(dateHelper.getDate());
    labJournal.setUpdatedBy(user);
    labJournal = labJournalDAO.createOrUpdate(labJournal);
    return labJournal;
  }

  public boolean deleteLabJournal(long labJournalId, String userName) {
    User user = userDAO.find(userName);
    return labJournalDAO.deleteLabJournal(labJournalId, user, dateHelper.getDate());
  }
}
