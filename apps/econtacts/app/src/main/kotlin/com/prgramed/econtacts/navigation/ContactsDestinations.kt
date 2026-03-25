package com.prgramed.econtacts.navigation

object ContactsDestinations {
    const val FAVORITES = "favorites"
    const val CONTACTS_LIST = "contacts_list"
    const val CONTACT_DETAIL = "contact_detail/{contactId}"
    const val CONTACT_EDIT = "contact_edit/{contactId}"
    const val CONTACT_NEW = "contact_new"
    const val RECENTS = "recents"
    const val SETTINGS = "settings"
    const val DUPLICATES = "duplicates"
    const val SPEED_DIAL = "speed_dial"
    const val DIALER = "dialer"
    const val CARDDAV_SETTINGS = "carddav_settings"
    const val VCARD_IMPORT = "vcard_import"

    fun contactDetail(contactId: Long) = "contact_detail/$contactId"
    fun contactEdit(contactId: Long) = "contact_edit/$contactId"
}
