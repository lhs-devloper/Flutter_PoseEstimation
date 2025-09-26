package com.example.pose_analysis_app.tracker

import com.example.pose_analysis_app.Data.Person

data class Track(
    val person: Person,
    val lastTimestamp: Long
)