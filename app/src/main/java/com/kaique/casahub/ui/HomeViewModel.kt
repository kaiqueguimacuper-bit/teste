package com.kaique.casahub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaique.casahub.data.HomeStore
import com.kaique.casahub.data.Prefs

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    val store = HomeStore(viewModelScope, Prefs(app))
}
