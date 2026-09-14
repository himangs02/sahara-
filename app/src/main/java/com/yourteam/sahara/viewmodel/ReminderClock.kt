package com.yourteam.sahara.viewmodel

import com.yourteam.sahara.data.repository.ReminderRepository
import com.yourteam.sahara.model.LocalClock
import com.yourteam.sahara.model.TodayReminder
import com.yourteam.sahara.model.forToday
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Emits the local clock now and at each minute boundary, so statuses roll over to missed and to a new day. */
fun minuteClock(now: () -> Long = System::currentTimeMillis): Flow<LocalClock> = flow {
    while (true) {
        val millis = now()
        emit(LocalClock.at(millis))
        delay(60_000 - millis % 60_000)
    }
}.distinctUntilChanged()

fun ReminderRepository.todayReminders(
    patientId: String,
    includeDisabled: Boolean,
    clock: Flow<LocalClock> = minuteClock()
): Flow<List<TodayReminder>> =
    getRemindersForPatient(patientId).combine(clock) { reminders, now -> reminders.forToday(now, includeDisabled) }
