package ru.ravel.rcriflayouttool.util

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import ru.ravel.rcriflayouttool.model.layout.DiagramLayout
import java.io.File


object CycleFinder {

	data class Edge(
		val from: String,
		val to: String,
		val exit: String,
	)

	data class Result(
		val cycles: List<List<String>>,
		val edges: List<Edge>,
		val totalNodes: Int,
		val totalEdges: Int,
	)


	fun findCycles(file: File): Result {
		val xmlMapper = XmlMapper()
		val layout: DiagramLayout = xmlMapper.readValue(file, DiagramLayout::class.java)

		// UID -> Reference
		val uidToRef: Map<String, String> = layout.elements?.diagramElements
			?.mapNotNull { el ->
				val uid = el.uid
				val ref = el.reference
				if (!uid.isNullOrBlank() && !ref.isNullOrBlank()) uid to ref else null
			}
			?.toMap()
			?: emptyMap()

		// Граф соседей (для Тарьяна) и список рёбер (для вывода exit)
		val graph = LinkedHashMap<String, MutableList<String>>()
		val edges = mutableListOf<Edge>()
		uidToRef.values.forEach { graph[it] = mutableListOf() }
		var edgeCount = 0

		layout.connections?.diagramConnections?.forEach { conn ->
			val pts = conn.endPoints?.points ?: emptyList()
			if (pts.size >= 2) {
				val a = pts[0]
				val b = pts[1]
				// Обычно exitPointRef стоит на исходной точке → берём её источником
				val srcEp = if (!a.exitPointRef.isNullOrBlank()) a else b
				val dstEp = if (srcEp === a) b else a

				val srcRef = srcEp.elementRef?.let(uidToRef::get)
				val dstRef = dstEp.elementRef?.let(uidToRef::get)
				val exit = srcEp.exitPointRef ?: ""

				if (srcRef != null && dstRef != null) {
					graph[srcRef]?.add(dstRef)
					edges += Edge(srcRef, dstRef, exit)
					edgeCount++
				}
			}
		}
		// 3. Алгоритм Тарьяна для поиска SCC
		val index = HashMap<String, Int>()
		val low = HashMap<String, Int>()
		val stack = ArrayDeque<String>()
		val onStack = HashSet<String>()
		val sccs = mutableListOf<List<String>>()
		var idx = 0

		fun strongConnect(v: String) {
			index[v] = idx
			low[v] = idx
			idx++
			stack.addLast(v)
			onStack.add(v)
			for (w in graph[v].orEmpty()) {
				if (!index.containsKey(w)) {
					strongConnect(w)
					low[v] = minOf(low[v]!!, low[w]!!)
				} else if (w in onStack) {
					low[v] = minOf(low[v]!!, index[w]!!)
				}
			}
			if (low[v] == index[v]) {
				val comp = mutableListOf<String>()
				while (true) {
					val w = stack.removeLast()
					onStack.remove(w)
					comp.add(w)
					if (w == v) break
				}
				sccs += comp
			}
		}

		graph.keys.forEach { if (!index.containsKey(it)) strongConnect(it) }

		val edgeSet = edges.map { it.from to it.to }.toSet()
		val cycles = sccs.filter { it.size > 1 || (it.size == 1 && edgeSet.contains(it[0] to it[0])) }

		return Result(
			cycles = cycles,
			edges = edges,
			totalNodes = graph.size,
			totalEdges = edgeCount
		)
	}

	fun formatCycles(res: Result): List<String> {
		return res.cycles.map { cycle ->
			buildString {
				for (i in cycle.indices) {
					val from = cycle[i]
					val to = cycle[(i + 1) % cycle.size]
					val exit = res.edges.find { it.from == from && it.to == to }?.exit ?: ""
					append("$from --$exit--> $to")
					if (i < cycle.size - 1) {
						append("\n")
					}
				}
			}
		}
	}
}
