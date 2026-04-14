package com.example.robotcommunication
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.fragment.app.Fragment

class TankControlFragment : Fragment(R.layout.tank_control){
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val leftSlider = view.findViewById<SeekBar>(R.id.leftTrackSeekBar)
        val rightSlider = view.findViewById<SeekBar>(R.id.rightTrackSeekBar)

        leftSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                TankControl.setLeftSpeed(progress)
                BluetoothService.sendCommand("LEFT:$progress")
            }

            override fun onStartTrackingTouch(seekBar : SeekBar) {

            }

            override fun onStopTrackingTouch(seekBar : SeekBar) {

            }
        })

        rightSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                TankControl.setRightSpeed(progress)
                BluetoothService.sendCommand("RIGHT:$progress")
            }

            override fun onStartTrackingTouch(seekBar : SeekBar) {

            }

            override fun onStopTrackingTouch(seekBar : SeekBar){

            }
        })
    }
}