package com.awolity.trakr.repository.remote.model

import com.awolity.trakr.data.entity.TrackEntity
import com.awolity.trakr.data.entity.TrackWithPoints
import com.awolity.trakr.data.entity.TrackpointEntity
import com.google.firebase.firestore.GeoPoint

fun trackWithpointsToFirestoreTrack(input: TrackWithPoints): FirestoreTrack {
    return FirestoreTrack(input.firebaseId, input.title, input.startTime, input.distance,
            input.ascent, input.descent, input.elapsedTime, input.numOfTrackPoints,
            input.northestPoint, input.southestPoint, input.westernPoint, input.easternPoint,
            input.minAltitude, input.maxAltitude, input.maxSpeed, input.avgSpeed,
            input.metadata,
            trackPointsToTimesArray(input.trackPoints),
            trackPointsToGeopointArray(input.trackPoints),
            trackPointsToSpeedArray(input.trackPoints),
            trackPointsToAltitudeArray(input.trackPoints))
}

fun trackEntityToFirestoreTrack(input: TrackEntity): FirestoreTrack {
    return FirestoreTrack(input.firebaseId, input.title, input.startTime, input.distance,
            input.ascent, input.descent, input.elapsedTime, input.numOfTrackPoints,
            input.northestPoint, input.southestPoint, input.westernPoint, input.easternPoint,
            input.minAltitude, input.maxAltitude, input.maxSpeed, input.avgSpeed,
            input.metadata)
}

private fun trackPointsToSpeedArray(input: List<TrackpointEntity>): List<Double> {
    return input.map { it.speed }
}

private fun trackPointsToTimesArray(input: List<TrackpointEntity>): List<Long> {
    return input.map { it.time }
}

private fun trackPointsToAltitudeArray(input: List<TrackpointEntity>): List<Double> {
    return input.map { it.altitude }
}

private fun trackPointsToGeopointArray(input: List<TrackpointEntity>): List<GeoPoint> {
    return input.map { GeoPoint(it.latitude, it.longitude) }
}

fun firestoreTrackToTrackWithPoints(input: FirestoreTrack): TrackWithPoints {
    val result = TrackWithPoints()
    result.firebaseId = input.firebaseId
    result.title = input.title
    result.startTime = input.startTime
    result.distance = input.distance
    result.ascent = input.ascent
    result.descent = input.descent
    result.elapsedTime = input.elapsedTime
    result.numOfTrackPoints = input.numOfTrackPoints
    result.northestPoint = input.northestPoint
    result.southestPoint = input.southestPoint
    result.westernPoint = input.westernPoint
    result.easternPoint = input.easternPoint
    result.minAltitude = input.minAltitude
    result.maxAltitude = input.maxAltitude
    result.maxSpeed = input.maxSpeed
    result.avgSpeed = input.avgSpeed
    result.metadata = input.metadata
    result.trackPoints = firestoreTrackToTrackPointEntities(input)
    return result
}

private fun firestoreTrackToTrackPointEntities(input: FirestoreTrack): List<TrackpointEntity> {
    val result = ArrayList<TrackpointEntity>(input.altitudes.size)
    for (i in 0 until input.altitudes.size) {
        val item = TrackpointEntity()
        item.speed = input.speeds[i]
        item.altitude = input.altitudes[i]
        item.time = input.times[i]
        item.latitude = input.points[i].latitude
        item.longitude = input.points[i].longitude
        result.add(item)
    }
    return result
}