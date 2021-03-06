package com.github.sebastianengel.cityguide.domain;

import android.location.Location;

import com.github.sebastianengel.cityguide.data.api.IPlacesApi;
import com.github.sebastianengel.cityguide.data.api.PlacesSearchResponse;
import com.github.sebastianengel.cityguide.data.model.Place;
import com.github.sebastianengel.cityguide.data.model.PlacesType;

import java.util.List;

import javax.inject.Singleton;

import pl.charmas.android.reactivelocation.ReactiveLocationProvider;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Domain layer service class for places related functionality.
 *
 * @author Sebastian Engel
 */
@Singleton
public class PlacesService {

    private final ReactiveLocationProvider locationProvider;
    private final IPlacesApi placesApi;

    public PlacesService(ReactiveLocationProvider locationProvider, IPlacesApi placesApi) {
        this.locationProvider = locationProvider;
        this.placesApi = placesApi;
    }

    public Observable<List<Place>> loadPlaces(final PlacesType placesType) {
        // TODO Check if location access is activated
        // TODO Handle LocationConnectionException when there are trouble connecting with Google Play Services
        // and other exceptions that can be thrown on
        // #getLastLocation(com.google.android.gms.common.api.GoogleApiClient).
        // Everything is delivered by {@link rx.Observer#onError(Throwable)}.

        return locationProvider.getLastKnownLocation()
            // With the location from the location provider, request nearby places from the Places API.
            // If the last known location is not available, throw a ServiceException to indicate a problem.
            .flatMap(new Func1<Location, Observable<PlacesSearchResponse>>() {
                @Override
                public Observable<PlacesSearchResponse> call(Location location) {
                    if (location != null) {
                        String locationString = location.getLatitude() + "," + location.getLongitude();
                        return placesApi.fetchNearbyPlaces(locationString, placesType.name().toLowerCase());
                    }

                    throw new ServiceException("User's last known location is not available (null).");
                }
            })
            // Check the response status and emit the places.
            // If the status is != OK, throw a ServiceException to indicate a problem.
            .flatMap(new Func1<PlacesSearchResponse, Observable<Place>>() {
                @Override
                public Observable<Place> call(PlacesSearchResponse response) {
                    if (response.status == PlacesSearchResponse.Status.OK) {
                        return Observable.from(response.places);
                    }

                    throw new ServiceException(String.format("Requesting places from Google Places API failed."
                            + " Status: %s, error message: %s", response.status, response.errorMessage));
                }
            })
            // Set the place type for each place.
            .map(new Func1<Place, Place>() {
                @Override
                public Place call(Place place) {
                    place.type = placesType;
                    return place;
                }
            })
            .toList()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
        ;

    }

}
