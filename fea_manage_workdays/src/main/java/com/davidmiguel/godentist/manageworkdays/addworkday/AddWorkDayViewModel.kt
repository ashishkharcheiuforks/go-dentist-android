@file:Suppress("EXPERIMENTAL_API_USAGE", "unused")

package com.davidmiguel.godentist.manageworkdays.addworkday

import androidx.lifecycle.*
import com.davidmiguel.godentist.core.R
import com.davidmiguel.godentist.core.data.clinics.ClinicsRepository
import com.davidmiguel.godentist.core.data.treatments.TreatmentsRepository
import com.davidmiguel.godentist.core.data.workdays.WorkDaysRepository
import com.davidmiguel.godentist.core.model.Clinic
import com.davidmiguel.godentist.core.model.Treatment
import com.davidmiguel.godentist.core.model.WorkDay
import com.davidmiguel.godentist.core.model.WorkDay.ExecutedTreatment
import com.davidmiguel.godentist.core.utils.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.*

private const val DEFAULT_DURATION = 8

class AddWorkDayViewModel(
    private val firebaseAuth: FirebaseAuth,
    private val workDaysRepository: WorkDaysRepository,
    private val clinicsRepository: ClinicsRepository,
    private val treatmentsRepository: TreatmentsRepository
) : ViewModel() {

    private val _screenStateAddWorkDay = MutableLiveData(ScreenState.INITIAL)
    val screenStateAddWorkDay: LiveData<ScreenState>
        get() = _screenStateAddWorkDay

    private val _screenStateAddWorkDayExecTreatment = MutableLiveData(ScreenState.INITIAL)
    val screenStateAddWorkDayExecTreatment: LiveData<ScreenState>
        get() = _screenStateAddWorkDayExecTreatment

    private val _snackbarEvent = MutableLiveData<Event<Int>>()
    val snackbarEvent: LiveData<Event<Int>>
        get() = _snackbarEvent

    private val _addWorkDayExecTreatmentEvent = MutableLiveData<Event<String>>()
    val addWorkDayExecTreatmentEvent: LiveData<Event<String>>
        get() = _addWorkDayExecTreatmentEvent

    private val _workDayUpdatedEvent = MutableLiveData<Event<Unit>>()
    val workDayUpdatedEvent: LiveData<Event<Unit>>
        get() = _workDayUpdatedEvent

    private val _workDayExecTreatmentUpdatedEvent = MutableLiveData<Event<Unit>>()
    val workDayExecTreatmentUpdatedEvent: LiveData<Event<Unit>>
        get() = _workDayExecTreatmentUpdatedEvent

    private val _clinics: MutableLiveData<List<Clinic>> = MutableLiveData(listOf())
    val clinics: LiveData<List<Clinic>>
        get() = _clinics
    private val _treatments: MutableLiveData<List<Treatment>> = MutableLiveData(listOf())
    val treatments: LiveData<List<Treatment>>
        get() = _treatments

    //----------------------------------------------------------------------------------------------
    // Add work day form fields
    //----------------------------------------------------------------------------------------------

    // Work day id
    private var workDayId: String? = null
    // Date
    val date = MutableLiveData<Long>(nowToEpochMilliUtc())
    private val _dateError = MutableLiveData(false)
    val dateError: LiveData<Boolean>
        get() = _dateError
    // Duration
    val duration = MutableLiveData<String>(DEFAULT_DURATION.toString())
    private val _durationError = MutableLiveData(false)
    val durationError: LiveData<Boolean>
        get() = _durationError
    // Clinic
    val clinic = MutableLiveData<Clinic>()
    private val _clinicError = MutableLiveData(false)
    val clinicError: LiveData<Boolean>
        get() = _clinicError
    // Executed treatments
    private val _executedTreatments: MutableLiveData<List<ExecutedTreatment>> =
        MutableLiveData(listOf())
    val executedTreatments: LiveData<List<ExecutedTreatment>>
        get() = _executedTreatments
    // Total earnings
    val totalEarnings = Transformations.map(_executedTreatments, ::calculateTotalEarnings)

    // Mood
    val mood = MutableLiveData(-1)
    private val _moodError = MutableLiveData(false)
    val moodError: LiveData<Boolean>
        get() = _moodError
    // Notes
    val notes = MutableLiveData<String>()
    private val _notesError = MutableLiveData(false)
    val notesError: LiveData<Boolean>
        get() = _notesError

    //----------------------------------------------------------------------------------------------
    // Add work day executed treatment form fields
    //----------------------------------------------------------------------------------------------

    // Executed treatment id
    private var executedTreatmentId: String? = null
    // Treatment
    val treatment = MutableLiveData<Treatment>()
    private val _treatmentError = MutableLiveData(false)
    val treatmentError: LiveData<Boolean>
        get() = _treatmentError
    // Price
    val price = MutableLiveData<String>()
    private val _priceError = MutableLiveData(false)
    val priceError: LiveData<Boolean>
        get() = _priceError
    val earnings = MutableLiveData<String>()
    private val _earningsError = MutableLiveData(false)
    val earningsError: LiveData<Boolean>
        get() = _earningsError

    //----------------------------------------------------------------------------------------------
    // Start screens
    //----------------------------------------------------------------------------------------------

    fun startAddWorkDay(workDayId: String?) {
        if (_screenStateAddWorkDay.value != ScreenState.INITIAL) return
        _screenStateAddWorkDay.value = ScreenState.LOADING_DATA
        // Load clinics
        loadClinics { clinics ->
            _clinics.value = clinics
            if (clinics.isNotEmpty() && clinic.value == null) {
                clinic.value = clinics.first()
            }
        }
        // Load workday
        if (workDayId == null) {
            _screenStateAddWorkDay.value = ScreenState.DATA_LOADED
            return
        }
        loadWorkDay(workDayId) { workDay ->
            this.workDayId = workDay.id
            date.value = workDay.date?.toDate()?.time
            duration.value = (workDay.duration?.run { this / 60F } ?: 0).toString()
            clinic.value = workDay.clinic
            _executedTreatments.value = workDay.executedTreatments
            mood.value = workDay.mood
            notes.value = workDay.notes
            _screenStateAddWorkDay.value = ScreenState.DATA_LOADED
        }
    }

    fun startAddWorkDayExecTreatment(executedTreatmentId: String?) {
        if (_screenStateAddWorkDayExecTreatment.value != ScreenState.INITIAL) return
        _screenStateAddWorkDayExecTreatment.value = ScreenState.LOADING_DATA
        // Load treatments
        loadTreatments { treatments ->
            _treatments.value = treatments
        }
        // Load executed treatment
        if (executedTreatmentId == null) {
            _screenStateAddWorkDayExecTreatment.value = ScreenState.DATA_LOADED
            return
        }
        loadWorkDayExecTreatment(executedTreatmentId) { executedTreatment ->
            this.executedTreatmentId = executedTreatment.id
            treatment.value = executedTreatment.treatment
            price.value = executedTreatment.price.toString()
            _screenStateAddWorkDayExecTreatment.value = ScreenState.DATA_LOADED
        }
    }

    //----------------------------------------------------------------------------------------------
    // Load data
    //----------------------------------------------------------------------------------------------

    private fun loadClinics(onSuccess: (List<Clinic>) -> Unit) {
        clinicsRepository.getAll(firebaseAuth.uid!!)
            .onEach { res ->
                when (res) {
                    is Success -> {
                        onSuccess(res.value)

                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadWorkDay(workDayId: String, onSuccess: (WorkDay) -> Unit) {
        workDaysRepository.get(firebaseAuth.uid!!, workDayId)
            .onEach { res ->
                when (res) {
                    is Success -> onSuccess(res.value)
                    is Failure -> _screenStateAddWorkDay.value = ScreenState.ERROR
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadTreatments(onSuccess: (List<Treatment>) -> Unit) {
        treatmentsRepository.getAll(firebaseAuth.uid!!)
            .onEach { res ->
                when (res) {
                    is Success -> onSuccess(res.value)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadWorkDayExecTreatment(
        executedTreatmentId: String,
        onSuccess: (ExecutedTreatment) -> Unit
    ) {
        val execTreatment = executedTreatments.value?.firstOrNull { it.id == executedTreatmentId }
        if (execTreatment != null) {
            onSuccess(execTreatment)
        } else {
            _screenStateAddWorkDayExecTreatment.value = ScreenState.ERROR
        }
    }

    //----------------------------------------------------------------------------------------------
    // Actions
    //----------------------------------------------------------------------------------------------

    fun addNewWorkDayExecTreatment() {
        _addWorkDayExecTreatmentEvent.value = Event("")
    }

    fun removeWorkDayExecTreatment(executedTreatmentId: String) {
        viewModelScope.launch {
            val list = _executedTreatments.value?.toMutableList() ?: return@launch
            list.removeIf { it.id == executedTreatmentId }
            _executedTreatments.postValue(list)
        }
    }

    fun saveExecTreatment() {
        viewModelScope.launch {
            _treatmentError.postValue(false)
            _priceError.postValue(false)
            _earningsError.postValue(false)

            // Id
            val currentId = executedTreatmentId ?: UUID.randomUUID().toString()
            // Treatment
            val currentTreatment = treatment.value
            if (currentTreatment == null) {
                _treatmentError.postValue(true)
                return@launch
            }
            // Price
            val currentPrice = price.value?.toDoubleOrNull()
            if (currentPrice == null) {
                _priceError.postValue(true)
                return@launch
            }
            // Earnings
            val currentEarnings = earnings.value?.toDoubleOrNull()
            if (currentEarnings == null) {
                _earningsError.postValue(true)
                return@launch
            }

            val executedTreatments = executedTreatments.value?.toMutableList() ?: mutableListOf()
            executedTreatments.removeIf { it.id == currentId }
            executedTreatments.add(
                ExecutedTreatment(
                    id = currentId,
                    treatment = currentTreatment,
                    price = currentPrice,
                    earnings = currentEarnings
                )
            )
            _executedTreatments.value = executedTreatments
            executedTreatmentId = null
            treatment.postValue(null)
            price.postValue(null)
            earnings.postValue(null)
            onWorkDayExecTreatmentSaved()
        }
    }

    private fun onWorkDayExecTreatmentSaved() {
        _workDayExecTreatmentUpdatedEvent.postValue(Event(Unit))
        _screenStateAddWorkDayExecTreatment.postValue(ScreenState.INITIAL)
    }

    fun calculateEarnings() {
        viewModelScope.launch {
            val currentPrice = price.value?.toDoubleOrNull() ?: return@launch
            val currentPercentage = clinic.value?.percentage ?: 0
            earnings.value = (currentPrice * currentPercentage / 100).toString()
        }
    }

    private fun calculateTotalEarnings(executedTreatments: List<ExecutedTreatment>): Double {
        return executedTreatments.sumByDouble { it.earnings ?: 0.0 }
    }

    fun onMoodSelected(mood: Int) {
        this.mood.value = mood
    }

    fun saveWorkDay() {
        viewModelScope.launch {
            _dateError.postValue(false)
            _durationError.postValue(false)
            _clinicError.postValue(false)
            _moodError.postValue(false)
            _notesError.postValue(false)

            // Id
            val currentId = workDayId ?: ""
            // Date
            val currentDate = date.value?.run { Timestamp(Date(this)) }
            if (currentDate == null) {
                _dateError.postValue(true)
                return@launch
            }
            // Duration
            val currentDuration = duration.value?.toFloatOrNull()?.run { (this * 60).toInt() }
            if (currentDuration == null) {
                _durationError.postValue(true)
                return@launch
            }
            // Clinic
            val currentClinic = clinic.value
            if (currentClinic == null) {
                _clinicError.postValue(true)
                return@launch
            }
            // Executed treatments
            val currentExecutedTreatments = _executedTreatments.value?.toMutableList()
                ?: mutableListOf()
            // Total earnings
            val currentTotalEarnings = currentExecutedTreatments.sumByDouble { it.earnings ?: 0.0 }
            // Mood
            val currentMood = mood.value
            if (currentMood == null) {
                _moodError.postValue(true)
                return@launch
            }
            // Notes
            val currentNotes = notes.value
            // Save workday (optimistic/offline first)
            onWorkDaySaved()
            workDaysRepository.put(
                firebaseAuth.uid!!,
                WorkDay(
                    currentId,
                    date = currentDate,
                    duration = currentDuration,
                    clinic = currentClinic,
                    executedTreatments = currentExecutedTreatments,
                    totalEarnings = currentTotalEarnings,
                    mood = currentMood,
                    notes = currentNotes
                )
            )
        }
    }

    private fun onWorkDaySaved() {
        _workDayUpdatedEvent.postValue(Event(Unit))
        _snackbarEvent.postValue(Event(R.string.all_dataSaved))
    }
}
