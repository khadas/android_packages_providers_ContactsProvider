/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.providers.contacts;

import com.android.internal.util.ArrayUtils;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Entity;
import android.content.EntityIterator;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.Presence;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.test.suitebuilder.annotation.LargeTest;

/**
 * Unit tests for {@link ContactsProvider2}.
 *
 * Run the test like this:
 * <code>
 * adb shell am instrument -w \
 *         com.android.providers.contacts.tests/android.test.InstrumentationTestRunner
 * </code>
 */
@LargeTest
public class ContactsProvider2Test extends BaseContactsProvider2Test {

    public void testRawContactsInsert() {
        ContentValues values = new ContentValues();

        values.put(RawContacts.ACCOUNT_NAME, "a");
        values.put(RawContacts.ACCOUNT_TYPE, "b");
        values.put(RawContacts.SOURCE_ID, "c");
        values.put(RawContacts.VERSION, 42);
        values.put(RawContacts.DIRTY, 1);
        values.put(RawContacts.DELETED, 1);
        values.put(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED);
        values.put(RawContacts.CUSTOM_RINGTONE, "d");
        values.put(RawContacts.SEND_TO_VOICEMAIL, 1);
        values.put(RawContacts.LAST_TIME_CONTACTED, 12345);
        values.put(RawContacts.STARRED, 1);
        values.put(RawContacts.SYNC1, "e");
        values.put(RawContacts.SYNC2, "f");
        values.put(RawContacts.SYNC3, "g");
        values.put(RawContacts.SYNC4, "h");

        Uri rowUri = mResolver.insert(RawContacts.CONTENT_URI, values);
        long rawContactId = ContentUris.parseId(rowUri);

        assertStoredValues(rowUri, values);
        assertSelection(RawContacts.CONTENT_URI, values, RawContacts._ID, rawContactId);
    }

    public void testDataInsert() {
        long rawContactId = createRawContactWithName("John", "Doe");

        ContentValues values = new ContentValues();
        putDataValues(values, rawContactId);
        Uri dataUri = mResolver.insert(Data.CONTENT_URI, values);
        long dataId = ContentUris.parseId(dataUri);

        long contactId = queryContactId(rawContactId);
        values.put(RawContacts.CONTACT_ID, contactId);
        assertStoredValues(dataUri, values);

        assertSelection(Data.CONTENT_URI, values, Data._ID, dataId);

        // Access the same data through the directory under RawContacts
        Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
        Uri rawContactDataUri =
                Uri.withAppendedPath(rawContactUri, RawContacts.Data.CONTENT_DIRECTORY);
        assertSelection(rawContactDataUri, values, Data._ID, dataId);

        // Access the same data through the directory under Contacts
        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        Uri contactDataUri = Uri.withAppendedPath(contactUri, Contacts.Data.CONTENT_DIRECTORY);
        assertSelection(contactDataUri, values, Data._ID, dataId);
    }

    public void testPhonesQuery() {
        ContentValues values = new ContentValues();
        values.put(RawContacts.CUSTOM_RINGTONE, "d");
        values.put(RawContacts.SEND_TO_VOICEMAIL, 1);
        values.put(RawContacts.LAST_TIME_CONTACTED, 12345);
        values.put(RawContacts.TIMES_CONTACTED, 54321);
        values.put(RawContacts.STARRED, 1);

        Uri rawContactUri = mResolver.insert(RawContacts.CONTENT_URI, values);
        long rawContactId = ContentUris.parseId(rawContactUri);

        insertStructuredName(rawContactId, "Meghan", "Knox");
        Uri uri = insertPhoneNumber(rawContactId, "18004664411");
        long phoneId = ContentUris.parseId(uri);


        long contactId = queryContactId(rawContactId);
        values.clear();
        values.put(Data._ID, phoneId);
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(RawContacts.CONTACT_ID, contactId);
        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values.put(Phone.NUMBER, "18004664411");
        values.put(Phone.TYPE, Phone.TYPE_HOME);
        values.putNull(Phone.LABEL);
        values.put(Contacts.DISPLAY_NAME, "Meghan Knox");
        values.put(Contacts.CUSTOM_RINGTONE, "d");
        values.put(Contacts.SEND_TO_VOICEMAIL, 1);
        values.put(Contacts.LAST_TIME_CONTACTED, 12345);
        values.put(Contacts.TIMES_CONTACTED, 54321);
        values.put(Contacts.STARRED, 1);

        assertSelection(Phone.CONTENT_URI, values, Data._ID, phoneId);
    }

    public void testPhonesFilterQuery() {
        long rawContactId = createRawContactWithName("Hot", "Tamale");
        insertPhoneNumber(rawContactId, "18004664411");

        Uri filterUri1 = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "tamale");
        ContentValues values = new ContentValues();
        values.put(Contacts.DISPLAY_NAME, "Hot Tamale");
        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values.put(Phone.NUMBER, "18004664411");
        values.put(Phone.TYPE, Phone.TYPE_HOME);
        values.putNull(Phone.LABEL);
        assertStoredValues(filterUri1, values);

        Uri filterUri2 = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "encilada");
        assertEquals(0, getCount(filterUri2, null, null));
    }

    // TODO fix and reenable the test
    public void _testPhoneLookup() {
        long rawContactId = createRawContactWithName("Hot", "Tamale");
        insertPhoneNumber(rawContactId, "18004664411");

        Uri lookupUri1 = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "8004664411");
        ContentValues values = new ContentValues();
        values.put(Contacts.DISPLAY_NAME, "Hot Tamale");
        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values.put(Phone.NUMBER, "18004664411");
        values.put(Phone.TYPE, Phone.TYPE_HOME);
        values.putNull(Phone.LABEL);
        assertStoredValues(lookupUri1, values);

        Uri lookupUri2 = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "4664411");
        assertEquals(0, getCount(lookupUri2, null, null));
    }

    public void testEmailsQuery() {
        ContentValues values = new ContentValues();
        values.put(RawContacts.CUSTOM_RINGTONE, "d");
        values.put(RawContacts.SEND_TO_VOICEMAIL, 1);
        values.put(RawContacts.LAST_TIME_CONTACTED, 12345);
        values.put(RawContacts.TIMES_CONTACTED, 54321);
        values.put(RawContacts.STARRED, 1);

        Uri rawContactUri = mResolver.insert(RawContacts.CONTENT_URI, values);
        long rawContactId = ContentUris.parseId(rawContactUri);

        insertStructuredName(rawContactId, "Meghan", "Knox");
        Uri uri = insertEmail(rawContactId, "meghan@acme.com");
        long emailId = ContentUris.parseId(uri);

        long contactId = queryContactId(rawContactId);
        values.clear();
        values.put(Data._ID, emailId);
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(RawContacts.CONTACT_ID, contactId);
        values.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        values.put(Email.DATA, "meghan@acme.com");
        values.put(Email.TYPE, Email.TYPE_HOME);
        values.putNull(Email.LABEL);
        values.put(Contacts.DISPLAY_NAME, "Meghan Knox");
        values.put(Contacts.CUSTOM_RINGTONE, "d");
        values.put(Contacts.SEND_TO_VOICEMAIL, 1);
        values.put(Contacts.LAST_TIME_CONTACTED, 12345);
        values.put(Contacts.TIMES_CONTACTED, 54321);
        values.put(Contacts.STARRED, 1);

        assertSelection(Email.CONTENT_URI, values, Data._ID, emailId);
    }

    public void testEmailsFilterQuery() {
        long rawContactId = createRawContactWithName("Hot", "Tamale");
        insertEmail(rawContactId, "tamale@acme.com");

        Uri filterUri1 = Uri.withAppendedPath(Email.CONTENT_FILTER_EMAIL_URI, "tamale@acme.com");
        ContentValues values = new ContentValues();
        values.put(Contacts.DISPLAY_NAME, "Hot Tamale");
        values.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        values.put(Email.DATA, "tamale@acme.com");
        values.put(Email.TYPE, Email.TYPE_HOME);
        values.putNull(Email.LABEL);
        assertStoredValues(filterUri1, values);

        Uri filterUri2 = Uri.withAppendedPath(Email.CONTENT_FILTER_EMAIL_URI, "encilada@acme.com");
        assertEquals(0, getCount(filterUri2, null, null));
    }

    public void testPostalsQuery() {
        long rawContactId = createRawContactWithName("Alice", "Nextore");
        Uri dataUri = insertPostalAddress(rawContactId, "1600 Amphiteatre Ave, Mountain View");
        long dataId = ContentUris.parseId(dataUri);

        long contactId = queryContactId(rawContactId);
        ContentValues values = new ContentValues();
        values.put(Data._ID, dataId);
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(RawContacts.CONTACT_ID, contactId);
        values.put(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE);
        values.put(StructuredPostal.FORMATTED_ADDRESS, "1600 Amphiteatre Ave, Mountain View");
        values.put(Contacts.DISPLAY_NAME, "Alice Nextore");

        assertSelection(StructuredPostal.CONTENT_URI, values, Data._ID, dataId);
    }

    public void testQueryContactData() {
        ContentValues values = new ContentValues();
        long contactId = createContact(values, "John", "Doe",
                "18004664411", "goog411@acme.com", Presence.INVISIBLE, 4, 1, 0);
        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);

        assertStoredValues(contactUri, values);
        assertSelection(Contacts.CONTENT_URI, values, Contacts._ID, contactId);
    }

    public void testQueryContactSummaryData() {
        ContentValues values = new ContentValues();
        long contactId = createContact(values, "John", "Doe",
                "18004664411", "goog411@acme.com", Presence.INVISIBLE, 4, 1, 0);
        values.put(Contacts.PRESENCE_STATUS, Presence.INVISIBLE);
        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_SUMMARY_URI, contactId);
        assertStoredValues(contactUri, values);
        assertSelection(Contacts.CONTENT_SUMMARY_URI, values, Contacts._ID, contactId);
    }

    public void testQueryContactSummaryFilterData() {
        ContentValues values = new ContentValues();
        createContact(values, "Stu", "Goulash", "18004664411",
                "goog411@acme.com", Presence.INVISIBLE, 4, 1, 0);
        values.put(Contacts.PRESENCE_STATUS, Presence.INVISIBLE);
        Uri filterUri1 = Uri.withAppendedPath(Contacts.CONTENT_SUMMARY_FILTER_URI, "goulash");
        assertStoredValues(filterUri1, values);

        Uri filterUri2 = Uri.withAppendedPath(Contacts.CONTENT_SUMMARY_FILTER_URI, "goolish");
        assertEquals(0, getCount(filterUri2, null, null));
    }

    public void testQueryContactSummaryStrequent() {
        ContentValues values1 = new ContentValues();
        createContact(values1, "Noah", "Tever", "18004664411",
                "a@acme.com", Presence.OFFLINE, 0, 0, 0);
        ContentValues values2 = new ContentValues();
        createContact(values2, "Sam", "Times", "18004664412",
                "b@acme.com", Presence.INVISIBLE, 3, 0, 0);
        ContentValues values3 = new ContentValues();
        createContact(values3, "Lotta", "Calling", "18004664413",
                "c@acme.com", Presence.AWAY, 5, 0, 0);
        ContentValues values4 = new ContentValues();
        createContact(values4, "Fay", "Veritt", "18004664414",
                "d@acme.com", Presence.AVAILABLE, 0, 1, 0);

        Cursor c = mResolver.query(Contacts.CONTENT_SUMMARY_STREQUENT_URI, null, null, null,
                Contacts._ID);
        assertEquals(3, c.getCount());
        c.moveToFirst();
        assertCursorValues(c, values4);
        c.moveToNext();
        assertCursorValues(c, values3);
        c.moveToNext();
        assertCursorValues(c, values2);
        c.close();

        Uri filterUri = Uri.withAppendedPath(Contacts.CONTENT_SUMMARY_STREQUENT_FILTER_URI, "fay");
        c = mResolver.query(filterUri, null, null, null, Contacts._ID);
        assertEquals(1, c.getCount());
        c.moveToFirst();
        assertCursorValues(c, values4);
        c.close();
    }

    public void testQueryContactSummaryGroup() {
        long groupId = createGroup(null, "testGroup", "Test Group");

        ContentValues values1 = new ContentValues();
        createContact(values1, "Best", "West", "18004664411",
                "west@acme.com", Presence.OFFLINE, 0, 0, groupId);

        ContentValues values2 = new ContentValues();
        createContact(values2, "Rest", "East", "18004664422",
                "east@acme.com", Presence.AVAILABLE, 0, 0, 0);

        Uri filterUri1 = Uri.withAppendedPath(Contacts.CONTENT_SUMMARY_GROUP_URI, "Test Group");
        Cursor c = mResolver.query(filterUri1, null, null, null, Contacts._ID);
        assertEquals(1, c.getCount());
        c.moveToFirst();
        assertCursorValues(c, values1);
        c.close();

        Uri filterUri2 = Uri.withAppendedPath(Contacts.CONTENT_SUMMARY_GROUP_URI, "Test Group");
        c = mResolver.query(filterUri2, null, Contacts.DISPLAY_NAME + "=?",
                new String[] { "Best West" }, Contacts._ID);
        assertEquals(1, c.getCount());
        c.close();

        Uri filterUri3 = Uri.withAppendedPath(Contacts.CONTENT_SUMMARY_GROUP_URI, "Next Group");
        c = mResolver.query(filterUri3, null, null, null, Contacts._ID);
        assertEquals(0, c.getCount());
        c.close();
    }

    public void testGroupInsert() {
        ContentValues values = new ContentValues();

        values.put(Groups.ACCOUNT_NAME, "a");
        values.put(Groups.ACCOUNT_TYPE, "b");
        values.put(Groups.SOURCE_ID, "c");
        values.put(Groups.VERSION, 42);
        values.put(Groups.DIRTY, 1);
        values.put(Groups.GROUP_VISIBLE, 1);
        values.put(Groups.TITLE, "d");
        values.put(Groups.TITLE_RES, 1234);
        values.put(Groups.NOTES, "e");
        values.put(Groups.RES_PACKAGE, "f");
        values.put(Groups.SYSTEM_ID, "g");
        values.put(Groups.DELETED, 1);
        values.put(Groups.SYNC1, "h");
        values.put(Groups.SYNC2, "i");
        values.put(Groups.SYNC3, "j");
        values.put(Groups.SYNC4, "k");

        Uri rowUri = mResolver.insert(Groups.CONTENT_URI, values);

        assertStoredValues(rowUri, values);
    }

    public void testDisplayNameParsingWhenPartsUnspecified() {
        long rawContactId = createRawContact();
        ContentValues values = new ContentValues();
        values.put(StructuredName.DISPLAY_NAME, "Mr.John Kevin von Smith, Jr.");
        insertStructuredName(rawContactId, values);

        assertStructuredName(rawContactId, "Mr", "John", "Kevin", "von Smith", "Jr");
    }

    public void testDisplayNameParsingWhenPartsSpecified() {
        long rawContactId = createRawContact();
        ContentValues values = new ContentValues();
        values.put(StructuredName.DISPLAY_NAME, "Mr.John Kevin von Smith, Jr.");
        values.put(StructuredName.FAMILY_NAME, "Johnson");
        insertStructuredName(rawContactId, values);

        assertStructuredName(rawContactId, null, null, null, "Johnson", null);
    }

    public void testSendToVoicemailDefault() {
        long rawContactId = createRawContactWithName();
        long contactId = queryContactId(rawContactId);

        Cursor c = queryContact(contactId);
        assertTrue(c.moveToNext());
        int sendToVoicemail = c.getInt(c.getColumnIndex(Contacts.SEND_TO_VOICEMAIL));
        assertEquals(0, sendToVoicemail);
        c.close();
    }

    public void testSetSendToVoicemailAndRingtone() {
        long rawContactId = createRawContactWithName();
        long contactId = queryContactId(rawContactId);

        updateSendToVoicemailAndRingtone(contactId, true, "foo");
        assertSendToVoicemailAndRingtone(contactId, true, "foo");
    }

    public void testSendToVoicemailAndRingtoneAfterAggregation() {
        long rawContactId1 = createRawContactWithName("a", "b");
        long contactId1 = queryContactId(rawContactId1);
        updateSendToVoicemailAndRingtone(contactId1, true, "foo");

        long rawContactId2 = createRawContactWithName("c", "d");
        long contactId2 = queryContactId(rawContactId2);
        updateSendToVoicemailAndRingtone(contactId2, true, "bar");

        // Aggregate them
        setAggregationException(AggregationExceptions.TYPE_KEEP_IN, contactId1, rawContactId2);

        // Both contacts had "send to VM", the contact now has the same value
        assertSendToVoicemailAndRingtone(contactId1, true, "foo,bar"); // Either foo or bar
    }

    public void testDoNotSendToVoicemailAfterAggregation() {
        long rawContactId1 = createRawContactWithName("e", "f");
        long contactId1 = queryContactId(rawContactId1);
        updateSendToVoicemailAndRingtone(contactId1, true, null);

        long rawContactId2 = createRawContactWithName("g", "h");
        long contactId2 = queryContactId(rawContactId2);
        updateSendToVoicemailAndRingtone(contactId2, false, null);

        // Aggregate them
        setAggregationException(AggregationExceptions.TYPE_KEEP_IN, contactId1, rawContactId2);

        // Since one of the contacts had "don't send to VM" that setting wins for the aggregate
        assertSendToVoicemailAndRingtone(contactId1, false, null);
    }

    public void testSetSendToVoicemailAndRingtonePreservedAfterJoinAndSplit() {
        long rawContactId1 = createRawContactWithName("i", "j");
        long contactId1 = queryContactId(rawContactId1);
        updateSendToVoicemailAndRingtone(contactId1, true, "foo");

        long rawContactId2 = createRawContactWithName("k", "l");
        long contactId2 = queryContactId(rawContactId2);
        updateSendToVoicemailAndRingtone(contactId2, false, "bar");

        // Aggregate them
        setAggregationException(AggregationExceptions.TYPE_KEEP_IN, contactId1, rawContactId2);

        // Split them
        setAggregationException(AggregationExceptions.TYPE_KEEP_OUT, contactId1, rawContactId2);

        assertSendToVoicemailAndRingtone(queryContactId(rawContactId1), true, "foo");
        assertSendToVoicemailAndRingtone(queryContactId(rawContactId2), false, "bar");
    }

    public void testSinglePresenceRowPerContact() {
        int protocol1 = Im.PROTOCOL_GOOGLE_TALK;
        String handle1 = "test@gmail.com";

        long rawContactId1 = createRawContact();
        insertImHandle(rawContactId1, protocol1, handle1);

        insertPresence(protocol1, handle1, Presence.AVAILABLE);
        insertPresence(protocol1, handle1, Presence.AWAY);
        insertPresence(protocol1, handle1, Presence.INVISIBLE);

        Cursor c = queryContactSummary(queryContactId(rawContactId1),
                new String[] {Presence.PRESENCE_STATUS});
        assertEquals(1, c.getCount());

        c.moveToFirst();
        assertEquals(Presence.AVAILABLE, c.getInt(0));

    }

    private void updateSendToVoicemailAndRingtone(long contactId, boolean sendToVoicemail,
            String ringtone) {
        ContentValues values = new ContentValues();
        values.put(Contacts.SEND_TO_VOICEMAIL, sendToVoicemail);
        if (ringtone != null) {
            values.put(Contacts.CUSTOM_RINGTONE, ringtone);
        }

        final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        int count = mResolver.update(uri, values, null, null);
        assertEquals(1, count);
    }

    private void assertSendToVoicemailAndRingtone(long contactId, boolean expectedSendToVoicemail,
            String expectedRingtone) {
        Cursor c = queryContact(contactId);
        assertTrue(c.moveToNext());
        int sendToVoicemail = c.getInt(c.getColumnIndex(Contacts.SEND_TO_VOICEMAIL));
        assertEquals(expectedSendToVoicemail ? 1 : 0, sendToVoicemail);
        String ringtone = c.getString(c.getColumnIndex(Contacts.CUSTOM_RINGTONE));
        if (expectedRingtone == null) {
            assertNull(ringtone);
        } else {
            assertTrue(ArrayUtils.contains(expectedRingtone.split(","), ringtone));
        }
        c.close();
    }

    public void testGroupCreationAfterMembershipInsert() {
        long rawContactId1 = createRawContact(mAccount);
        Uri groupMembershipUri = insertGroupMembership(rawContactId1, "gsid1");

        long groupId = assertSingleGroup(NO_LONG, mAccount, "gsid1", null);
        assertSingleGroupMembership(ContentUris.parseId(groupMembershipUri),
                rawContactId1, groupId, "gsid1");
    }

    public void testGroupReuseAfterMembershipInsert() {
        long rawContactId1 = createRawContact(mAccount);
        long groupId1 = createGroup(mAccount, "gsid1", "title1");
        Uri groupMembershipUri = insertGroupMembership(rawContactId1, "gsid1");

        assertSingleGroup(groupId1, mAccount, "gsid1", "title1");
        assertSingleGroupMembership(ContentUris.parseId(groupMembershipUri),
                rawContactId1, groupId1, "gsid1");
    }

    public void testGroupInsertFailureOnGroupIdConflict() {
        long rawContactId1 = createRawContact(mAccount);
        long groupId1 = createGroup(mAccount, "gsid1", "title1");

        ContentValues values = new ContentValues();
        values.put(GroupMembership.RAW_CONTACT_ID, rawContactId1);
        values.put(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
        values.put(GroupMembership.GROUP_SOURCE_ID, "gsid1");
        values.put(GroupMembership.GROUP_ROW_ID, groupId1);
        try {
            mResolver.insert(Data.CONTENT_URI, values);
            fail("the insert was expected to fail, but it succeeded");
        } catch (IllegalArgumentException e) {
            // this was expected
        }
    }

    public void testContentEntityIterator() throws RemoteException {
        // create multiple contacts and check that the selected ones are returned
        long id;

        long groupId1 = createGroup(mAccount, "gsid1", "title1");
        long groupId2 = createGroup(mAccount, "gsid2", "title2");

        id = createRawContact(mAccount, RawContacts.SOURCE_ID, "c0");
        insertGroupMembership(id, "gsid1");
        insertEmail(id, "c0@email.com");
        insertPhoneNumber(id, "5551212c0");

        long c1 = id = createRawContact(mAccount, RawContacts.SOURCE_ID, "c1");
        Uri id_1_0 = insertGroupMembership(id, "gsid1");
        Uri id_1_1 = insertGroupMembership(id, "gsid2");
        Uri id_1_2 = insertEmail(id, "c1@email.com");
        Uri id_1_3 = insertPhoneNumber(id, "5551212c1");

        long c2 = id = createRawContact(mAccount, RawContacts.SOURCE_ID, "c2");
        Uri id_2_0 = insertGroupMembership(id, "gsid1");
        Uri id_2_1 = insertEmail(id, "c2@email.com");
        Uri id_2_2 = insertPhoneNumber(id, "5551212c2");

        long c3 = id = createRawContact(mAccount, RawContacts.SOURCE_ID, "c3");
        Uri id_3_0 = insertGroupMembership(id, groupId2);
        Uri id_3_1 = insertEmail(id, "c3@email.com");
        Uri id_3_2 = insertPhoneNumber(id, "5551212c3");

        EntityIterator iterator = mResolver.queryEntities(
                maybeAddAccountQueryParameters(RawContacts.CONTENT_URI, mAccount),
                RawContacts.SOURCE_ID + " in ('c1', 'c2', 'c3')", null, null);
        Entity entity;
        ContentValues[] subValues;
        entity = iterator.next();
        assertEquals(c1, (long) entity.getEntityValues().getAsLong(RawContacts._ID));
        subValues = asSortedContentValuesArray(entity.getSubValues());
        assertEquals(4, subValues.length);
        assertDataRow(subValues[0], GroupMembership.CONTENT_ITEM_TYPE,
                Data._ID, id_1_0,
                GroupMembership.GROUP_ROW_ID, groupId1,
                GroupMembership.GROUP_SOURCE_ID, "gsid1");
        assertDataRow(subValues[1], GroupMembership.CONTENT_ITEM_TYPE,
                Data._ID, id_1_1,
                GroupMembership.GROUP_ROW_ID, groupId2,
                GroupMembership.GROUP_SOURCE_ID, "gsid2");
        assertDataRow(subValues[2], Email.CONTENT_ITEM_TYPE,
                Data._ID, id_1_2,
                Email.DATA, "c1@email.com");
        assertDataRow(subValues[3], Phone.CONTENT_ITEM_TYPE,
                Data._ID, id_1_3,
                Email.DATA, "5551212c1");

        entity = iterator.next();
        assertEquals(c2, (long) entity.getEntityValues().getAsLong(RawContacts._ID));
        subValues = asSortedContentValuesArray(entity.getSubValues());
        assertEquals(3, subValues.length);
        assertDataRow(subValues[0], GroupMembership.CONTENT_ITEM_TYPE,
                Data._ID, id_2_0,
                GroupMembership.GROUP_ROW_ID, groupId1,
                GroupMembership.GROUP_SOURCE_ID, "gsid1");
        assertDataRow(subValues[1], Email.CONTENT_ITEM_TYPE,
                Data._ID, id_2_1,
                Email.DATA, "c2@email.com");
        assertDataRow(subValues[2], Phone.CONTENT_ITEM_TYPE,
                Data._ID, id_2_2,
                Email.DATA, "5551212c2");

        entity = iterator.next();
        assertEquals(c3, (long) entity.getEntityValues().getAsLong(RawContacts._ID));
        subValues = asSortedContentValuesArray(entity.getSubValues());
        assertEquals(3, subValues.length);
        assertDataRow(subValues[0], GroupMembership.CONTENT_ITEM_TYPE,
                Data._ID, id_3_0,
                GroupMembership.GROUP_ROW_ID, groupId2,
                GroupMembership.GROUP_SOURCE_ID, "gsid2");
        assertDataRow(subValues[1], Email.CONTENT_ITEM_TYPE,
                Data._ID, id_3_1,
                Email.DATA, "c3@email.com");
        assertDataRow(subValues[2], Phone.CONTENT_ITEM_TYPE,
                Data._ID, id_3_2,
                Email.DATA, "5551212c3");

        assertFalse(iterator.hasNext());
        iterator.close();
    }

    public void testDataCreateUpdateDeleteByMimeType() throws Exception {
        long rawContactId = createRawContact();

        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, "testmimetype");
        values.put(Data.RES_PACKAGE, "oldpackage");
        values.put(Data.IS_PRIMARY, 1);
        values.put(Data.IS_SUPER_PRIMARY, 1);
        values.put(Data.DATA1, "old1");
        values.put(Data.DATA2, "old2");
        values.put(Data.DATA3, "old3");
        values.put(Data.DATA4, "old4");
        values.put(Data.DATA5, "old5");
        values.put(Data.DATA6, "old6");
        values.put(Data.DATA7, "old7");
        values.put(Data.DATA8, "old8");
        values.put(Data.DATA9, "old9");
        values.put(Data.DATA10, "old10");
        values.put(Data.DATA11, "old11");
        values.put(Data.DATA12, "old12");
        values.put(Data.DATA13, "old13");
        values.put(Data.DATA14, "old14");
        values.put(Data.DATA15, "old15");
        Uri uri = mResolver.insert(Data.CONTENT_URI, values);
        assertStoredValues(uri, values);

        values.clear();
        values.put(Data.RES_PACKAGE, "newpackage");
        values.put(Data.IS_PRIMARY, 0);
        values.put(Data.IS_SUPER_PRIMARY, 0);
        values.put(Data.DATA1, "new1");
        values.put(Data.DATA2, "new2");
        values.put(Data.DATA3, "new3");
        values.put(Data.DATA4, "new4");
        values.put(Data.DATA5, "new5");
        values.put(Data.DATA6, "new6");
        values.put(Data.DATA7, "new7");
        values.put(Data.DATA8, "new8");
        values.put(Data.DATA9, "new9");
        values.put(Data.DATA10, "new10");
        values.put(Data.DATA11, "new11");
        values.put(Data.DATA12, "new12");
        values.put(Data.DATA13, "new13");
        values.put(Data.DATA14, "new14");
        values.put(Data.DATA15, "new15");
        mResolver.update(Data.CONTENT_URI, values, Data.RAW_CONTACT_ID + "=" + rawContactId +
                " AND " + Data.MIMETYPE + "='testmimetype'", null);

        // Should not be able to change IS_PRIMARY and IS_SUPER_PRIMARY by the above update
        values.put(Data.IS_PRIMARY, 1);
        values.put(Data.IS_SUPER_PRIMARY, 1);
        assertStoredValues(uri, values);

        int count = mResolver.delete(Data.CONTENT_URI, Data.RAW_CONTACT_ID + "=" + rawContactId
                + " AND " + Data.MIMETYPE + "='testmimetype'", null);
        assertEquals(1, count);
        assertEquals(0, getCount(Data.CONTENT_URI, Data.RAW_CONTACT_ID + "=" + rawContactId
                        + " AND " + Data.MIMETYPE + "='testmimetype'", null));
    }

    public void testRawContactDeletion() {
        long rawContactId = createRawContact();
        Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);

        insertImHandle(rawContactId, Im.PROTOCOL_GOOGLE_TALK, "deleteme@android.com");
        insertPresence(Im.PROTOCOL_GOOGLE_TALK, "deleteme@android.com", Presence.AVAILABLE);
        assertEquals(1, getCount(Uri.withAppendedPath(uri, RawContacts.Data.CONTENT_DIRECTORY),
                null, null));
        assertEquals(1, getCount(Presence.CONTENT_URI, Presence.RAW_CONTACT_ID + "=" + rawContactId,
                null));

        mResolver.delete(uri, null, null);

        assertStoredValues(uri, RawContacts.DELETED, "1");

        Uri permanentDeletionUri = uri.buildUpon().appendQueryParameter(
                RawContacts.DELETE_PERMANENTLY, "true").build();
        mResolver.delete(permanentDeletionUri, null, null);
        assertEquals(0, getCount(uri, null, null));
        assertEquals(0, getCount(Uri.withAppendedPath(uri, RawContacts.Data.CONTENT_DIRECTORY),
                null, null));
        assertEquals(0, getCount(Presence.CONTENT_URI, Presence.RAW_CONTACT_ID + "=" + rawContactId,
                null));
    }

    public void testRawContactDirtySetOnChange() {
        Uri uri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI,
                createRawContact(mAccount));
        assertDirty(uri, true);
        clearDirty(uri);
        assertDirty(uri, false);
    }

    public void testRawContactDirtyAndVersion() {
        final long rawContactId = createRawContact(mAccount);
        Uri uri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawContactId);
        assertDirty(uri, true);
        long version = getVersion(uri);

        ContentValues values = new ContentValues();
        values.put(ContactsContract.RawContacts.DIRTY, 0);
        values.put(ContactsContract.RawContacts.SEND_TO_VOICEMAIL, 1);
        values.put(ContactsContract.RawContacts.AGGREGATION_MODE,
                RawContacts.AGGREGATION_MODE_IMMEDITATE);
        values.put(ContactsContract.RawContacts.STARRED, 1);
        assertEquals(1, mResolver.update(uri, values, null, null));
        assertEquals(version, getVersion(uri));

        assertDirty(uri, false);

        Uri emailUri = insertEmail(rawContactId, "goo@woo.com");
        assertDirty(uri, true);
        ++version;
        assertEquals(version, getVersion(uri));
        clearDirty(uri);

        values = new ContentValues();
        values.put(Email.DATA, "goo@hoo.com");
        mResolver.update(emailUri, values, null, null);
        assertDirty(uri, true);
        ++version;
        assertEquals(version, getVersion(uri));
        clearDirty(uri);

        mResolver.delete(emailUri, null, null);
        assertDirty(uri, true);
        ++version;
        assertEquals(version, getVersion(uri));
    }

    public void testRawContactClearDirty() {
        final long rawContactId = createRawContact(mAccount);
        Uri uri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI,
                rawContactId);
        long version = getVersion(uri);
        insertEmail(rawContactId, "goo@woo.com");
        assertDirty(uri, true);
        version++;
        assertEquals(version, getVersion(uri));

        clearDirty(uri);
        assertDirty(uri, false);
        assertEquals(version, getVersion(uri));
    }

    public void testRawContactDeletionSetsDirty() {
        final long rawContactId = createRawContact(mAccount);
        Uri uri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI,
                rawContactId);
        long version = getVersion(uri);
        clearDirty(uri);
        assertDirty(uri, false);

        mResolver.delete(uri, null, null);
        assertStoredValues(uri, RawContacts.DELETED, "1");
        assertDirty(uri, true);
        version++;
        assertEquals(version, getVersion(uri));
    }

    private long createContact(ContentValues values, String firstName, String givenName,
            String phoneNumber, String email, int presenceStatus, int timesContacted, int starred,
            long groupId) {
        values.put(RawContacts.STARRED, starred);
        values.put(RawContacts.SEND_TO_VOICEMAIL, 1);
        values.put(RawContacts.CUSTOM_RINGTONE, "beethoven5");
        values.put(RawContacts.LAST_TIME_CONTACTED, 12345);
        values.put(RawContacts.TIMES_CONTACTED, timesContacted);
        Uri rawContactUri = mResolver.insert(RawContacts.CONTENT_URI, values);
        long rawContactId = ContentUris.parseId(rawContactUri);
        insertStructuredName(rawContactId, firstName, givenName);
        Uri photoUri = insertPhoto(rawContactId);
        long photoId = ContentUris.parseId(photoUri);
        values.put(Contacts.PHOTO_ID, photoId);
        Uri phoneUri = insertPhoneNumber(rawContactId, phoneNumber);
        long phoneId = ContentUris.parseId(phoneUri);
        values.put(Contacts.PRIMARY_PHONE_ID, phoneId);
        Uri emailUri = insertEmail(rawContactId, email);
        long emailId = ContentUris.parseId(emailUri);
        values.put(Contacts.PRIMARY_EMAIL_ID, emailId);

        insertPresence(Im.PROTOCOL_GOOGLE_TALK, email, presenceStatus);

        if (groupId != 0) {
            insertGroupMembership(rawContactId, groupId);
        }

        // FIXME: should not have to set these as primaries explicitly. They should be
        // returned as defaults
        ContentValues primaryValues = new ContentValues();
        primaryValues.clear();
        primaryValues.put(Data.IS_PRIMARY, 1);
        primaryValues.put(Data.IS_SUPER_PRIMARY, 1);
        mResolver.update(phoneUri, primaryValues, null, null);
        mResolver.update(emailUri, primaryValues, null, null);

        return queryContactId(rawContactId);
    }

    private void putDataValues(ContentValues values, long rawContactId) {
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, "testmimetype");
        values.put(Data.RES_PACKAGE, "oldpackage");
        values.put(Data.IS_PRIMARY, 1);
        values.put(Data.IS_SUPER_PRIMARY, 1);
        values.put(Data.DATA1, "one");
        values.put(Data.DATA2, "two");
        values.put(Data.DATA3, "three");
        values.put(Data.DATA4, "four");
        values.put(Data.DATA5, "five");
        values.put(Data.DATA6, "six");
        values.put(Data.DATA7, "seven");
        values.put(Data.DATA8, "eight");
        values.put(Data.DATA9, "nine");
        values.put(Data.DATA10, "ten");
        values.put(Data.DATA11, "eleven");
        values.put(Data.DATA12, "twelve");
        values.put(Data.DATA13, "thirteen");
        values.put(Data.DATA14, "fourteen");
        values.put(Data.DATA15, "fifteen");
        values.put(Data.SYNC1, "sync1");
        values.put(Data.SYNC2, "sync2");
        values.put(Data.SYNC3, "sync3");
        values.put(Data.SYNC4, "sync4");
    }
}
