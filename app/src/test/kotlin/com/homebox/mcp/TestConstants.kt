package com.homebox.mcp

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object TestConstants {
	val TEST_ID_1 = Uuid.random()
	val TEST_ID_2 = Uuid.random()
	val TEST_ID_3 = Uuid.random()
	val TEST_ID_4 = Uuid.random()
	val TEST_ID_5 = Uuid.random()
}
