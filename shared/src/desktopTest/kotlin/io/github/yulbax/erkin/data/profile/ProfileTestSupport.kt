package io.github.yulbax.erkin.data.profile

const val TEST_GROUP_NAME = "Group"

suspend fun ProfileRepository.add(raw: String, fileName: String? = null): ProfileOperationResult =
    add(raw, fileName, AddTarget.Main(TEST_GROUP_NAME))
