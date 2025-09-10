package ru.ravel.rcriflayouttool.util

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.File

object GitUnit {

	fun getPreviousFileVersion(repoDir: File, fileAbsPath: String): String {
		val repo = FileRepositoryBuilder().findGitDir(repoDir).build()
		repo.use { r ->
			val workTree = r.workTree
			val rel = workTree.toPath().relativize(File(fileAbsPath).toPath()).toString().replace("\\", "/")
			val prevCommit = Git(r).log()
				.addPath(rel)
				.setMaxCount(2)
				.call()
				.toList()
				.getOrNull(0)
				?: return ""
			return readFileAtCommit(r.workTree, prevCommit.name, rel)
		}
	}


	private fun readFileAtCommit(repoWorkTree: File, commitId: String, relPath: String): String {
		val repo = FileRepositoryBuilder().findGitDir(repoWorkTree).build()
		repo.use { r ->
			val rev = r.resolve(commitId) ?: return ""
			val commit = RevWalk(r).use { it.parseCommit(rev) }
			val tree = commit.tree

			TreeWalk(r).use { tw ->
				tw.addTree(tree)
				tw.isRecursive = true
				tw.filter = PathFilter.create(relPath)
				while (tw.next()) {
					if (tw.pathString == relPath) {
						val bytes = r.open(tw.getObjectId(0)).bytes
						return XmlUtil.readXmlSafe(bytes)
					}
				}
			}
			return ""
		}
	}
}