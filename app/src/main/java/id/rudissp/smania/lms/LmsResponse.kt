package id.rudissp.smania.lms

import com.google.gson.annotations.SerializedName

data class LmsResponse(
    @SerializedName("status") val status: String,
    @SerializedName("kelas") val kelas: String,
    @SerializedName("data") val data: List<LmsItem>
)