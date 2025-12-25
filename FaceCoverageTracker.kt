package com.example.faceid.coverage

import com.example.faceid.coverage.FaceCoverageTracker.Bin
import java.sql.Time

class FaceCoverageTracker {

      enum class AXIS {
         YAW, PITCH, ROLL
     }

     var coveredBins = 0f


    //creating bins
     data class Bin(
         var center: Float,
         var range: Float,
         var accTime: Float = 0f
     )


    //Counter for covered spaces
    private val bins: MutableMap<AXIS, MutableMap<String, Bin>> = mutableMapOf(
        AXIS.YAW to mutableMapOf(
            "Y1" to Bin(-30f, 10f, 0f),
            "Y2" to Bin(-15f, 10f,  0f),
            "Y3" to Bin(0f, 10f,   0f),
            "Y4" to Bin(15f, 10f, 0f),
            "Y5" to Bin(30f, 10f, 0f)
        ),
        AXIS.PITCH to mutableMapOf(
            "P1" to Bin(-20f, 20f, 0f),
            "P2" to Bin(0f, 20f, 0f),
            "P3" to Bin(20f, 20f, 0f)
        ),
        AXIS.ROLL to mutableMapOf(
            "R1" to Bin(-20f, 20f, 0f),
            "R2" to Bin(0f, 20f, 0f),
            "R3" to Bin(20f, 20f, 0f)
        )
    )



    private var lastTime: Long = System.nanoTime()//Keep it Long

    fun giveTime(time: Long): Float {
        val deltaTime = (time - lastTime) / 1_000_000_000f // Convert to Nanoseconds
        lastTime = time
        return deltaTime
    }
    fun getCoveragePercentage (): Int{
        val totalBins = 11f
        return ((coveredBins/totalBins)*100f).toInt()
    }

    // Build a code that takes the current yaw, pitch, roll, adds the time into each bin
    fun updateBins(yaw: Float, pitch: Float, roll: Float) {
        val time = System.nanoTime()
        val delta = giveTime(time)
        bins[AXIS.YAW]?.let {updateBin(yaw, it, delta)}
        bins[AXIS.PITCH]?.let {updateBin(pitch, it, delta)}
        bins[AXIS.ROLL]?.let {updateBin(roll, it, delta)} // Corrected this line
    }

    fun updateBin(value: Float, binMap: MutableMap<String, Bin>, delta: Float) {

        for (bin in binMap.values){
            if (bin.accTime>=1f){
                continue
            }
            if (Math.abs(bin.center - value) <= bin.range ) {
                bin.accTime += delta
                if(bin.accTime>=1f){
                    coveredBins+=1f
                }
            }
        }
    }


    //Build a code that checks if what the covered count is and updates the FaceID logo to show the percentage covered




}
