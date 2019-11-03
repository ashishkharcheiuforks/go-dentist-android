package com.davidmiguel.godentist.core.data.clinics

import com.davidmiguel.godentist.core.data.COLLECTION_CLINICS
import com.davidmiguel.godentist.core.data.COLLECTION_USERS
import com.davidmiguel.godentist.core.model.Clinic
import com.davidmiguel.godentist.core.utils.Result
import com.davidmiguel.godentist.core.utils.asFlow
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.Flow

class ClinicsRepository {

    private val db by lazy { Firebase.firestore }

    // TODO refactor to live data
    public fun exists(uid: String, clinicId: String): Task<Boolean> {
        return getClinicDocumentRef(uid, clinicId).get().continueWith {
            it.result?.exists() == true
        }
    }

    // TODO refactor to live data
    public fun get(uid: String, clinicId: String): Task<Clinic?> {
        return getClinicDocumentRef(uid, clinicId).get().continueWith {
            it.result?.toObject<Clinic>()
        }
    }

    public fun getAll(uid: String): Flow<Result<List<Clinic>>> {
        return getClinicsCollectionRef(uid).asFlow(Clinic::class.java)
    }

    /**
     * Updates (or creates if it doesn't exist) a clinic.
     * To create a clinic the id must be empty.
     * TODO refactor to live data
     */
    public fun update(uid: String, clinic: Clinic): Task<Void> {
        if (clinic.id.isEmpty()) { // Create document if it doesn't exists
            clinic.id = getClinicsCollectionRef(uid).document().id
        }
        return getClinicDocumentRef(uid, clinic.id).set(clinic)
    }

    private fun getClinicsCollectionRef(uid: String): CollectionReference {
        return db.collection(COLLECTION_USERS).document(uid).collection(COLLECTION_CLINICS)
    }

    private fun getClinicDocumentRef(uid: String, clinicId: String): DocumentReference {
        return getClinicsCollectionRef(uid).document(clinicId)
    }
}