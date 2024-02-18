
import _ from 'lodash'
import { createSVGWindow } from 'svgdom'
import { Box, SVG, Svg, registerWindow } from '@svgdotjs/svg.js'
import xmlFormat from 'xml-formatter'

const log = console.log

// Cube is SIDE x SIDE x SIDE
const SIDE = 4

type Encoded = bigint

// All bits of cube set
const FULL: Encoded = 0xffffffffffffffffn

// Empty cube
const EMPTY: Encoded = 0n

type Block = [number, number, number]
type Shape = Block[]

// Array of shape num x blocks x {x,y,z}
const shapes: Shape[] = [
	[[0,0,0],[0,1,0],[0,2,0],[1,1,0],[1,1,1]],
	[[0,0,0],[0,1,0],[0,2,0],[1,0,0],[0,1,1]],
	[[0,0,0],[0,1,0],[0,2,0],[1,0,1],[0,0,1]],
	[[0,0,0],[0,1,0],[0,2,0],[0,0,1],[1,0,0]],
	[[0,0,0],[0,1,0],[0,1,1],[1,1,1],[1,2,1]],
	[[0,0,0],[0,1,0],[0,1,1],[0,2,1],[1,1,1]],
	[[0,1,0],[1,0,0],[1,1,0],[1,2,0],[2,1,0]],
	[[0,0,0],[0,1,0],[0,1,1],[1,1,1]],
	[[0,0,0],[0,1,0],[0,2,0],[1,1,0],[0,1,1]],
	[[0,1,0],[0,2,0],[1,0,0],[1,1,0],[2,1,0]],
	[[0,0,0],[0,1,0],[1,0,0],[0,1,1],[0,2,1]],
	[[0,0,0],[0,1,0],[0,2,0],[1,0,0],[0,2,1]],
	[[0,1,0],[0,2,0],[1,0,0],[1,1,0],[2,0,0]],
]

enum Axis {X=0, Y=1, Z=2}

// Rotate block around axis
function rotateBlock ([x, y, z]: Block , axis: Axis): Block {
	switch (axis) {
		case Axis.X: return [x, z, -y] 
		case Axis.Y: return [-z, y, x]
		case Axis.Z: return [y, -x, z]
	}
}

// Rotate shape about axis
function rotateShape (shape: Shape, axis: Axis): Shape {
	return shape.map(block => rotateBlock(block, axis))
}

// Add all rotations about X axis
function xAxisRotations(shape: Shape): Shape[] {
	const r1 = rotateShape(shape, Axis.X)
	const r2 = rotateShape(r1, Axis.X)
	const r3 = rotateShape(r2, Axis.X)
	return [shape, r1, r2, r3]
}

// Return all 6x4 possible rotations of the shape
function translate (shape: Shape): Shape[]  {

	// Get all y axis translations
	const t1 = rotateShape(shape, Axis.Y)
	const t2 = rotateShape(t1, Axis.Y)
	const t3 = rotateShape(t2, Axis.Y)

	// Add missing z axis translations
	const t4 = rotateShape(shape, Axis.Z)
	const t5 = rotateShape(t2, Axis.Z)
	
	return [shape, t1, t2, t3, t4, t5].flatMap(xAxisRotations)
}

function axisRange(shape: Shape, axis: Axis): [number, number] {
	const aMin = _.min(shape.map(b => b[axis]))!
	const aMax = _.max(shape.map(b => b[axis]))!
	return [aMin, 4-(aMax-aMin)]
}

function encodeBlock([x, y, z]: Block): Encoded {
	return (1n << BigInt(x*SIDE*SIDE+y*SIDE+z))
}

// Encode shape array as long
function encode (shape: Shape): Encoded {
	return _.reduce(shape, (a, b) => {
		return a | encodeBlock(b)
	}, 0n)
}

// Return shape shifted by x,y,z amounts
function shift(shape: Shape, dx: number, dy: number, dz: number): Shape {
	const os: Shape =  shape.map(([x, y, z]) => [x+dx, y+dy, z+dz])
	return os
}

// Return all positions the shape could occupy in the cube
function allShapeEncodings (shape: Shape): Encoded[] {
	
	const [xMin, xWid] = axisRange(shape,Axis.X)
	const [yMin, yWid] = axisRange(shape,Axis.Y)
	const [zMin, zWid] = axisRange(shape,Axis.Z)

	// Add all encoded positions into array
	return _.range(0, xWid).flatMap (dx => 
		_.range(0, yWid).flatMap (dy => 
			_.range(0, zWid).map (dz => 
				encode(shift(shape, dx-xMin, dy-yMin, dz-zMin))
			)
		)
	)
}

function searchPiece(cube: Encoded, positions: Encoded[], remaining: Encoded[][], placed: Encoded[]): Encoded[] {
	if (_.isEmpty(positions)) return []
	const position = _.head(positions)!
	const result = search(cube | position, remaining, [...placed, position])
	if (!_.isEmpty(result)) return result
	return searchPiece(cube, _.tail(positions)!, remaining, placed)
}

// Work through each shape discarding those that clash with a filled position
function search(cube: Encoded, remaining: Encoded[][], placed: Encoded[]): Encoded[] {
	if (cube === FULL) {return placed}
	const others = _.tail(remaining)!
	const candidates = _.head(remaining)!.filter((c) => {
		if ((c & cube) !== EMPTY) {return false}
		return !prune(c | cube, others)
	})
	return searchPiece(cube, candidates, others, placed)	
}

const e2s = (encoded: Encoded) => encoded.toString(2).padStart(64, "0")

function logEnc(encoded: Encoded, name: string = "") {
	log(name.padEnd(8), e2s(encoded))
}

// Prune a search if either
// - There is a piece that can no longer fit
// - There is an enclosed hole in the cube
function prune(cube: Encoded, remaining: Encoded[][]): boolean {	
	var testCube = cube
	var allFit = remaining.findIndex((r) => {
		const candidates = r.filter((s) => (cube & s) === EMPTY)
		candidates.forEach(c => testCube |= c)
		return _.isEmpty(candidates)
	}) === -1
	return (!allFit) || (testCube !== FULL)
}

function allTranslatedShapeEncodings(s: Shape): Encoded[] {
	const set = new Set(translate(s).flatMap(t => allShapeEncodings(t)))
	return [...set]
}

const allShapeTranslations: Encoded[][] = shapes.map(allTranslatedShapeEncodings)


// Decode encoded back to shape
function decode(shape: Encoded): Shape {
	return _.range(0, 4).flatMap (x => 
		_.range(0, 4).flatMap (y => 
			_.range(0, 4).flatMap (z => {
				const b: Block = [x, y, z]
				const e = encodeBlock(b)
				return ((e & shape) === e) ? [b] : []
			})
		)
	)
}
	
// Render blocks as SVG
function toSVG(solution: Shape[]): string {

	const svg: Svg = SVG()

	const box: Box = new Box('0 0 5000 4000')

	svg.width(1000).height(800).viewbox(box)
	const panel1 = svg.defs().polygon([[0, 0], [0, 100], [86.6, 50], [86.6, -50]])
	const box1 = svg.defs().group()
		.add(svg.use(panel1).rotate(-120, 0, 0))
		.add(svg.use(panel1).rotate(0, 0, 0))
		.add(svg.use(panel1).rotate(120, 0, 0))
	const block1 = svg.defs().use(box1).fill("pink")
	const cube1 = svg.defs().use(box1).stroke({dasharray: "1,3"}).rotate(180, 0, 0).translate(0, -100).scale(4).translate(0,-200)
	
	// Write shape definitions
	const shapes1 = solution.map((shape, i) => {
		const sorted = _.sortBy(shape, ([x,y,z]) => -(x+y-100*z))
		const shape1 = svg.defs().group()
		sorted.forEach(([x, y, z]) => {
			const tx = 86.60254 * (x-y)
			const ty = -50 * (x+y) - 100 * z
			shape1.add(svg.use(block1).translate(tx, ty))
		})
		return shape1
	})

	const main1 = svg.group().fill("none").stroke("black")

	shapes1.map((s, i) => {
		const x = 800 + (i % 5) * 800
		const y = 1000 + Math.floor(i / 5) * 1000
		main1.use(cube1).move(x, y)
		main1.text(i.toString()).font({'font-size': 60}).move(x, y+150)
		main1.use(s).move(x, y)
	})
	
	svg.svg()
	return xmlFormat(svg.svg())

}

function registerSvgWindow() {
	const window = createSVGWindow()
	const document = window.document
	registerWindow(window, document)	
}

export const exampleSolution = [
	3146001n, 131682n, 9223528167505920000n, 39969481052651520n, 71339008n, 1152975380693385216n,
	549898944640n, 838864896n, 281556581154816n, 70371965427712n, 891712863658311680n, 292058038284n,
	7138205409382236160n
]

async function render(solution: Encoded[], file: string) {	
	registerSvgWindow()
	const shapes = solution.map(decode)
	const svg = toSVG(shapes)
	await Bun.write(file, svg)	
	log(`SVG written to ${file}`)
}

function run() {
	const start = Date.now()
	log("Running (takes around 20s) ...")
	const solution = search(EMPTY, allShapeTranslations, [])	// Takes around 20s
	const end = Date.now()
	log("=== Result ===")
	solution.forEach((p) => logEnc(p))
	log("Time taken", Math.round((end-start)/1000), "seconds")
	render(solution, "solution.svg")
}

run()
