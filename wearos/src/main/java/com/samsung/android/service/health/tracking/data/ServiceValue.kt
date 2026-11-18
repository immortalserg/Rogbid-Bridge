package com.samsung.android.service.health.tracking.data

import android.os.Parcel
import android.os.Parcelable

/** Wire-compatible representation of Samsung Health Platform's ServiceValue. */
class ServiceValue(
 val integerValue: Int,
 val floatValue: Float,
 val longValue: Long
) : Parcelable {
 constructor(parcel: Parcel) : this(parcel.readInt(), parcel.readFloat(), parcel.readLong())
 override fun describeContents() = 0
 override fun writeToParcel(dest: Parcel, flags: Int) {
  dest.writeInt(integerValue)
  dest.writeFloat(floatValue)
  dest.writeLong(longValue)
 }
 companion object {
  @JvmField val CREATOR = object : Parcelable.Creator<ServiceValue> {
   override fun createFromParcel(source: Parcel) = ServiceValue(source)
   override fun newArray(size: Int): Array<ServiceValue?> = arrayOfNulls(size)
  }
 }
}
