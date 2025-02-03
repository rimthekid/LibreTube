package com.github.libretube.api

import android.content.Context
import com.github.libretube.R
import com.github.libretube.api.obj.PipedInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstanceRepository(private val context: Context) {

    /**
     * Fetch official public instances from kavin.rocks
     */
    suspend fun getInstances(): Result<List<PipedInstance>> = withContext(Dispatchers.IO) {
        runCatching {
            RetrofitInstance.externalApi.getInstances(PIPED_INSTANCES_URL)
        }
    }

    fun getInstancesFallback(): List<PipedInstance> {
        val instanceNames = context.resources.getStringArray(R.array.instances)
        return context.resources.getStringArray(R.array.instancesValue)
            .mapIndexed { index, instanceValue ->
                PipedInstance(instanceNames[index], instanceValue)
            }
    }

    companion object {
        private const val PIPED_INSTANCES_URL = "https://piped-instances.kavin.rocks"
    }
}
