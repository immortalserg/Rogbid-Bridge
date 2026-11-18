package com.samsung.android.service.health.tracking.data

import android.os.Parcel
import android.os.Parcelable

/** Wire-compatible representation of Samsung Health Platform's ServiceDataPoint. */
class ServiceDataPoint(
 val timeStamp: Long,
 val serviceValue: List<ServiceValue>
) : Parcelable {
 constructor(parcel: Parcel) : this(
  parcel.readLong(),
  mutableListOf<ServiceValue>().also { parcel.readList(it, ServiceValue::class.java.classLoader) }
 )
 override fun describeContents() = 0
 override fun writeToParcel(dest: Parcel, flags: Int) {
  dest.writeLong(timeStamp)
  dest.writeList(serviceValue)
 }
 companion object {
  @JvmField val CREATOR = object : Parcelable.Creator<ServiceDataPoint> {
   override fun createFromParcel(source: Parcel) = ServiceDataPoint(source)
   override fun newArray(size: Int): Array<ServiceDataPoint?> = arrayOfNulls(size)
  }
 }
}
